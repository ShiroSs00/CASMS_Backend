package com.casms.pos;

import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.common.util.NewWriteCurrency;
import com.casms.payment.entity.OnboardAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Virtual spending allowance and immutable usage history, never a cash balance or payment. */
@Service @RequiredArgsConstructor @Transactional
public class PosLedgerService {
    private final JdbcTemplate jdbc;
    private static final String USAGE_TYPES="transaction_type IN ('CHARGE','VOID','ADJUSTMENT')";

    public void lockAccount(UUID id) {
        if(jdbc.queryForList("SELECT onboard_account_id FROM \"OnboardAccount\" WHERE onboard_account_id=? FOR UPDATE",UUID.class,id).isEmpty())
            throw new ResourceNotFoundException("Onboard account",id);
    }
    public PosDtos.Budget budget(OnboardAccount account,UUID passenger) {
        UUID id=account.getOnboardAccountId();
        if(account.getCreditLimit()==null || account.getCreditLimit().signum()<0)
            throw new BusinessException("POS_ALLOWANCE_NOT_ALLOCATED","A finite internal spending allowance must be allocated first");
        var members=jdbc.queryForList("SELECT account_member_id FROM \"OnboardAccountMember\" WHERE onboard_account_id=? AND passenger_id=? "
                +"AND status='ACTIVE' AND charge_permission='ALLOWED' AND (effective_from IS NULL OR effective_from<=now()) "
                +"AND (effective_to IS NULL OR effective_to>now()) FOR UPDATE",UUID.class,id,passenger);
        if(members.isEmpty())throw new AccessDeniedException("Passenger has no active spending permission on this account");
        if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM \"OnboardTransaction\" WHERE onboard_account_id=? AND currency_code<>'USD' "
                +"AND status='POSTED' AND "+USAGE_TYPES+")",Boolean.class,id)))
            throw new BusinessException("POS_ACCOUNT_CURRENCY_MISMATCH","Cannot mix historical account currencies");
        BigDecimal used=jdbc.queryForObject("SELECT coalesce(sum(amount),0) FROM \"OnboardTransaction\" WHERE onboard_account_id=? AND status='POSTED' AND "+USAGE_TYPES,BigDecimal.class,id).max(BigDecimal.ZERO);
        BigDecimal accountRemaining=account.getCreditLimit().subtract(used).max(BigDecimal.ZERO);
        BigDecimal remaining=accountRemaining;
        for(var limit:jdbc.queryForList("SELECT limit_amount,currency_code,effective_from FROM \"SpendingLimit\" WHERE account_member_id=? AND status='ACTIVE' "
                +"AND effective_from<=now() AND (effective_to IS NULL OR effective_to>now()) FOR SHARE",members.getFirst())) {
            NewWriteCurrency.requireUsd((String)limit.get("currency_code"));
            BigDecimal spent=jdbc.queryForObject("SELECT coalesce(sum(amount),0) FROM \"OnboardTransaction\" WHERE onboard_account_id=? AND passenger_id=? "
                    +"AND status='POSTED' AND "+USAGE_TYPES+" AND transaction_time>=?",BigDecimal.class,id,passenger,limit.get("effective_from"));
            remaining=remaining.min(((BigDecimal)limit.get("limit_amount")).subtract(spent.max(BigDecimal.ZERO)).max(BigDecimal.ZERO));
        }
        return new PosDtos.Budget(id,passenger,account.getCreditLimit(),used,accountRemaining,remaining,"USD");
    }
    public void assertCanSpend(OnboardAccount account,UUID passenger,BigDecimal amount) {
        var budget=budget(account,passenger);
        if(amount.compareTo(budget.accountRemaining())>0)throw new BusinessException("POS_ALLOWANCE_EXCEEDED","Insufficient internal account allowance");
        if(amount.compareTo(budget.remainingAmount())>0)throw new BusinessException("POS_SPENDING_LIMIT_EXCEEDED","Insufficient passenger spending allowance");
    }
    public List<PosDtos.History> history(UUID account) {
        return jdbc.query("SELECT * FROM \"OnboardTransaction\" WHERE onboard_account_id=? AND status='POSTED' AND "+USAGE_TYPES
                +" ORDER BY transaction_time,transaction_id",(r,n)->new PosDtos.History(PosRepository.uuid(r,"transaction_id"),r.getString("transaction_type"),
                        r.getBigDecimal("amount"),r.getString("source_type"),PosRepository.uuid(r,"source_reference_id"),
                        PosRepository.uuid(r,"created_by_user_id"),PosRepository.instant(r,"transaction_time")),account);
    }
    public boolean isPosRequest(UUID request) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM \"PosSale\" WHERE service_request_id=?)",Boolean.class,request));
    }
    /** Called by the shared request transition flow, including the original non-POS APIs. */
    public void reverseForRequest(UUID request,UUID actor) {
        var accountIds=jdbc.queryForList("SELECT onboard_account_id FROM \"PosSale\" WHERE service_request_id=?",UUID.class,request);
        if(accountIds.isEmpty())return;
        lockAccount(accountIds.getFirst());
        var rows=jdbc.queryForList("SELECT s.sale_id,s.amount,s.onboard_account_id,c.passenger_id,c.transaction_id FROM \"PosSale\" s "
                +"JOIN \"Charge\" c ON c.charge_id=s.charge_id WHERE s.service_request_id=? AND s.reversed_at IS NULL FOR UPDATE OF s",request);
        if(rows.isEmpty())return;
        var sale=rows.getFirst();UUID reversal=UUID.randomUUID();
        jdbc.update("INSERT INTO \"OnboardTransaction\" (transaction_id,onboard_account_id,passenger_id,transaction_type,amount,currency_code,"
                +"transaction_time,posted_at,status,source_type,source_reference_id,original_transaction_id,created_by_user_id,sync_status,synced_at) "
                +"VALUES (?,?,?,'VOID',?,'USD',now(),now(),'POSTED','POS_ALLOWANCE_REVERSAL',?,?,?,'SYNCED',now())",
                reversal,sale.get("onboard_account_id"),sale.get("passenger_id"),((BigDecimal)sale.get("amount")).negate(),
                sale.get("sale_id"),sale.get("transaction_id"),actor);
        jdbc.update("UPDATE \"PosSale\" SET reversed_at=now(),reversal_transaction_id=? WHERE sale_id=?",reversal,sale.get("sale_id"));
    }
}
