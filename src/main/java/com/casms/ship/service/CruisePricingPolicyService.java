package com.casms.ship.service;

import com.casms.booking.repository.BookingQuoteRepository;
import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.identity.entity.User;
import com.casms.identity.repository.UserRepository;
import com.casms.ship.dto.request.CruisePricingPolicyRequest;
import com.casms.ship.dto.response.CruisePricingPolicyResponse;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.CruisePricingPolicy;
import com.casms.ship.enums.CabinType;
import com.casms.ship.enums.PricingPolicyStatus;
import com.casms.ship.repository.CabinRepository;
import com.casms.ship.repository.CruisePricingPolicyRepository;
import com.casms.ship.repository.CruiseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CruisePricingPolicyService {

    private final CruisePricingPolicyRepository pricingPolicyRepository;
    private final CruiseRepository cruiseRepository;
    private final CabinRepository cabinRepository;
    private final BookingQuoteRepository bookingQuoteRepository;
    private final UserRepository userRepository;
    private final CruisePreparationService cruisePreparationService;

    @Transactional(readOnly = true)
    public CruisePricingPolicyResponse getPricingPolicy(UUID cruiseId) {
        Cruise cruise = cruiseRepository.findById(cruiseId)
                .orElseThrow(() -> new ResourceNotFoundException("Cruise", cruiseId));

        CruisePricingPolicy policy = pricingPolicyRepository.findByCruise_CruiseId(cruiseId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "PRICING_POLICY_NOT_FOUND",
                        "Chính sách giá cho chuyến đi " + cruise.getCruiseCode() + " chưa được thiết lập"));

        return toResponse(policy);
    }

    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper;
    private final CruiseMainflowService plans;

    private void actor(UUID actorId, boolean review) {
        var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if(auth==null || !(auth.getPrincipal() instanceof com.casms.security.principal.CasmsPrincipal principal)
            || !Objects.equals(actorId,principal.getUserId())
            || !principal.hasRole(review?com.casms.identity.enums.Role.OPERATIONS_MANAGER:com.casms.identity.enums.Role.CRUISE_MANAGER)
            || !principal.hasPermission(review?com.casms.identity.enums.Permission.CRUISE_PRICING_APPROVE:com.casms.identity.enums.Permission.CRUISE_UPDATE_DRAFT))
            throw new org.springframework.security.access.AccessDeniedException("Pricing actor requires role and effective permission");
    }
    private BusinessException conflict(String code){return new BusinessException(HttpStatus.CONFLICT,code,"Pricing policy state or revision changed");}
    private void revision(CruisePricingPolicy policy,Long expected){
        if(expected==null || expected!=(policy==null?0:policy.getRevision()))throw conflict("STALE_REVISION");
    }
    private String amount(BigDecimal value){return value==null?null:value.stripTrailingZeros().toPlainString();}
    private String snapshot(CruisePricingPolicy p){
        var values=new java.util.TreeMap<String,Object>();
        values.put("currency",p.getCurrency());values.put("policyNumber",p.getPolicyNumber());values.put("policyVersion",p.getPolicyVersion());
        values.put("interiorBaseFare",amount(p.getInteriorBaseFare()));values.put("oceanViewBaseFare",amount(p.getOceanViewBaseFare()));
        values.put("balconyBaseFare",amount(p.getBalconyBaseFare()));values.put("suiteBaseFare",amount(p.getSuiteBaseFare()));
        values.put("portFeePerPassenger",amount(p.getPortFeePerPassenger()));values.put("taxRate",amount(p.getTaxRate()));values.put("depositPercentage",amount(p.getDepositPercentage()));
        values.put("finalPaymentDueDays",p.getFinalPaymentDueDays());values.put("fullPaymentCutoffDays",p.getFullPaymentCutoffDays());values.put("cancellationPolicy",p.getCancellationPolicy());
        try{return mapper.writeValueAsString(values);}catch(Exception e){throw new IllegalStateException(e);}
    }
    private String hash(String content){try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private void event(CruisePricingPolicy p,String action,UUID actor,String reason){
        pricingPolicyRepository.flush();
        jdbc.update("INSERT INTO \"CruisePricingEvent\"(event_id,pricing_policy_id,cruise_id,revision,action,actor_id,reason,snapshot) VALUES(?,?,?,?,?,?,?,?)",UUID.randomUUID(),p.getPricingPolicyId(),p.getCruise().getCruiseId(),p.getRevision(),action,actor,reason,snapshot(p));
    }
    /** Compatibility Java overload; approval flags have no authority. */
    @Transactional
    public CruisePricingPolicyResponse savePricingPolicy(UUID cruiseId,CruisePricingPolicyRequest request,UUID actorId,boolean ignored){return savePricingPolicy(cruiseId,request,actorId);}
    @Transactional
    public CruisePricingPolicyResponse savePricingPolicy(UUID cruiseId,CruisePricingPolicyRequest request,UUID actorId){
        actor(actorId,false);plans.assertEditable(cruiseId);
        var cruise=cruiseRepository.findByIdForUpdate(cruiseId).orElseThrow(()->new ResourceNotFoundException("Cruise",cruiseId));
        var current=pricingPolicyRepository.findByCruise_CruiseId(cruiseId).orElse(null);revision(current,request.getExpectedRevision());
        if(!"USD".equals(request.getCurrency()))throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,"CURRENCY_USD_REQUIRED","New pricing writes require USD");
        if(current!=null&&current.getStatus()==PricingPolicyStatus.PENDING_APPROVAL)throw conflict("PRICING_POLICY_PENDING");
        boolean successor=current==null||!"USD".equals(current.getCurrency())||current.getStatus()==PricingPolicyStatus.APPROVED||current.getStatus()==PricingPolicyStatus.RETURNED||current.getStatus()==PricingPolicyStatus.ARCHIVED;
        var policy=successor?new CruisePricingPolicy():current;
        long nextRevision=current==null?1:current.getRevision()+1;
        if(successor){policy.setCruise(cruise);policy.setPolicyNumber(current==null?1:current.getPolicyNumber()+1);}
        policy.setPolicyVersion("PRC-"+cruise.getCruiseCode()+"-V"+policy.getPolicyNumber());policy.setRevision(nextRevision);
        policy.setCurrency("USD");policy.setInteriorBaseFare(request.getInteriorBaseFare());policy.setOceanViewBaseFare(request.getOceanViewBaseFare());
        policy.setBalconyBaseFare(request.getBalconyBaseFare());policy.setSuiteBaseFare(request.getSuiteBaseFare());
        policy.setPortFeePerPassenger(request.getPortFeePerPassenger());policy.setTaxRate(request.getTaxRate());policy.setDepositPercentage(request.getDepositPercentage());
        policy.setFinalPaymentDueDays(request.getFinalPaymentDueDays());policy.setFullPaymentCutoffDays(request.getFullPaymentCutoffDays());policy.setCancellationPolicy(request.getCancellationPolicy());
        policy.setStatus(PricingPolicyStatus.DRAFT);policy.setUpdatedAt(Instant.now());policy=pricingPolicyRepository.saveAndFlush(policy);
        jdbc.update("INSERT INTO \"CruisePricingMaker\"(pricing_policy_id,user_id) VALUES(?,?) ON CONFLICT DO NOTHING",policy.getPricingPolicyId(),actorId);
        bookingQuoteRepository.invalidateActiveQuotesByCruiseId(cruiseId);event(policy,"SAVE_DRAFT",actorId,null);
        cruisePreparationService.invalidateSignoffs(cruiseId,"Pricing draft revision changed");
        return toResponse(policy);
    }
    public record Review(Long expectedRevision,String reason){}
    @Transactional
    public CruisePricingPolicyResponse review(UUID cruiseId,String action,Review request,UUID actorId){
        boolean submit=action.equals("submit");actor(actorId,!submit);
        cruiseRepository.findByIdForUpdate(cruiseId).orElseThrow(()->new ResourceNotFoundException("Cruise",cruiseId));
        var p=pricingPolicyRepository.findByCruise_CruiseId(cruiseId).orElseThrow(()->new ResourceNotFoundException("Pricing policy",cruiseId));revision(p,request.expectedRevision());
        if(submit){
            if(p.getStatus()!=PricingPolicyStatus.DRAFT)throw conflict("INVALID_PRICING_TRANSITION");
            if(!"USD".equals(p.getCurrency()))throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,"CURRENCY_USD_REQUIRED","Pricing submission requires USD");
            validateFaresForApproval(p.getCruise(),p);p.setSubmittedSnapshot(snapshot(p));p.setSnapshotHash(hash(p.getSubmittedSnapshot()));p.setSubmittedBy(actorId);p.setSubmittedAt(Instant.now());p.setStatus(PricingPolicyStatus.PENDING_APPROVAL);
        }else{
            if(p.getStatus()!=PricingPolicyStatus.PENDING_APPROVAL)throw conflict("INVALID_PRICING_TRANSITION");
            if(Objects.equals(p.getSubmittedBy(),actorId)||Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM \"CruisePricingMaker\" WHERE pricing_policy_id=? AND user_id=?)",Boolean.class,p.getPricingPolicyId(),actorId)))
                throw new BusinessException(HttpStatus.FORBIDDEN,"SELF_APPROVAL_FORBIDDEN","Pricing maker cannot review own revision");
            if(!Objects.equals(p.getSubmittedSnapshot(),snapshot(p))||!Objects.equals(p.getSnapshotHash(),hash(snapshot(p))))throw conflict("PRICING_SNAPSHOT_STALE");
            if(action.equals("return")){
                if(request.reason()==null||request.reason().isBlank())throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,"REASON_REQUIRED","Pricing return requires reason");
                p.setStatus(PricingPolicyStatus.RETURNED);p.setDecisionReason(request.reason().trim());
            }else if(action.equals("approve")){
                validateFaresForApproval(p.getCruise(),p);p.setStatus(PricingPolicyStatus.APPROVED);p.setApprovedBy(userRepository.findById(actorId).orElseThrow());p.setApprovedAt(Instant.now());
            }else throw new IllegalArgumentException("Unsupported pricing action");
            p.setReviewedBy(actorId);p.setReviewedAt(Instant.now());
        }
        p.setRevision(p.getRevision()+1);event(p,action.toUpperCase(java.util.Locale.ROOT),actorId,request.reason());return toResponse(p);
    }
    @Transactional
    public void recallForPlan(UUID cruiseId, UUID actorId, boolean reviewer) {
        var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if(auth==null || !(auth.getPrincipal() instanceof com.casms.security.principal.CasmsPrincipal principal)
            || !Objects.equals(actorId,principal.getUserId())
            || !principal.hasRole(reviewer?com.casms.identity.enums.Role.OPERATIONS_MANAGER:com.casms.identity.enums.Role.CRUISE_MANAGER)
            || !principal.hasPermission(reviewer?com.casms.identity.enums.Permission.ITINERARY_APPROVE:com.casms.identity.enums.Permission.CRUISE_UPDATE_DRAFT))
            throw new org.springframework.security.access.AccessDeniedException("Plan recall requires role and effective permission");
        cruiseRepository.findByIdForUpdate(cruiseId).orElseThrow(()->new ResourceNotFoundException("Cruise",cruiseId));
        var p=pricingPolicyRepository.findByCruise_CruiseId(cruiseId).orElseThrow(()->new ResourceNotFoundException("Pricing policy",cruiseId));
        if(p.getStatus()!=PricingPolicyStatus.PENDING_APPROVAL)return;
        p.setStatus(PricingPolicyStatus.DRAFT);p.setSubmittedBy(null);p.setSubmittedAt(null);
        p.setSubmittedSnapshot(null);p.setSnapshotHash(null);p.setRevision(p.getRevision()+1);
        event(p,"PLAN_RECALL",actorId,null);
    }
    /** Legacy no-revision route cannot approve. */
    @Transactional
    public CruisePricingPolicyResponse approvePricingPolicy(UUID cruiseId,UUID actorId){actor(actorId,true);throw conflict("STALE_REVISION");}
    @Transactional(readOnly=true)
    public java.util.List<java.util.Map<String,Object>> history(UUID id){
        cruiseRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("Cruise",id));
        return jdbc.queryForList("SELECT pricing_policy_id,revision,action,actor_id,reason,snapshot,created_at FROM \"CruisePricingEvent\" WHERE cruise_id=? ORDER BY created_at,event_id",id);
    }

    private void validateFaresForApproval(Cruise cruise, CruisePricingPolicy policy) {
        // If ship is assigned, verify all cabin types on ship have configured (non-null) base fares
        if (cruise.getShip() != null) {
            UUID shipId = cruise.getShip().getShipId();
            for (CabinType type : CabinType.values()) {
                long count = cabinRepository.countByShipShipIdAndCabinType(shipId, type);
                if (count > 0 && policy.getBaseFareForCabinType(type) == null) {
                    throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "PRICING_POLICY_FARE_MISSING",
                            "Tàu có hạng phòng " + type + " nhưng chưa được cấu hình giá cơ sở trong chính sách");
                }
            }
        } else {
            // General validation: at least Interior base fare must be configured
            if (policy.getInteriorBaseFare() == null) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "PRICING_POLICY_FARE_MISSING",
                        "Chính sách giá phải cấu hình ít nhất giá cơ sở phòng Interior");
            }
        }

        if (policy.getDepositPercentage() == null || policy.getDepositPercentage().compareTo(BigDecimal.ZERO) < 0
                || policy.getDepositPercentage().compareTo(BigDecimal.ONE) > 0) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_DEPOSIT_PERCENTAGE",
                    "Tỷ lệ đặt cọc không hợp lệ (phải từ 0 đến 1.0)");
        }
    }

    private String bumpVersion(String currentVersion) {
        if (currentVersion == null || currentVersion.isBlank()) {
            return "PRC-V2";
        }
        try {
            int lastV = currentVersion.lastIndexOf("-V");
            if (lastV != -1) {
                String numStr = currentVersion.substring(lastV + 2);
                int num = Integer.parseInt(numStr);
                return currentVersion.substring(0, lastV + 2) + (num + 1);
            }
        } catch (Exception ignored) {
        }
        return currentVersion + "-rev" + (System.currentTimeMillis() % 10000);
    }

    public CruisePricingPolicyResponse toResponse(CruisePricingPolicy policy) {
        Cruise cruise = policy.getCruise();
        User approver = policy.getApprovedBy();

        return CruisePricingPolicyResponse.builder()
                .pricingPolicyId(policy.getPricingPolicyId())
                .policyNumber(policy.getPolicyNumber()).revision(policy.getRevision()).submittedBy(policy.getSubmittedBy()).submittedAt(policy.getSubmittedAt()).reviewedBy(policy.getReviewedBy()).reviewedAt(policy.getReviewedAt()).decisionReason(policy.getDecisionReason()).snapshotHash(policy.getSnapshotHash())
                .cruiseId(cruise != null ? cruise.getCruiseId() : null)
                .cruiseCode(cruise != null ? cruise.getCruiseCode() : null)
                .cruiseName(cruise != null ? cruise.getCruiseName() : null)
                .policyVersion(policy.getPolicyVersion())
                .status(policy.getStatus())
                .currency(policy.getCurrency())
                .interiorBaseFare(policy.getInteriorBaseFare())
                .oceanViewBaseFare(policy.getOceanViewBaseFare())
                .balconyBaseFare(policy.getBalconyBaseFare())
                .suiteBaseFare(policy.getSuiteBaseFare())
                .portFeePerPassenger(policy.getPortFeePerPassenger())
                .taxRate(policy.getTaxRate())
                .depositPercentage(policy.getDepositPercentage())
                .finalPaymentDueDays(policy.getFinalPaymentDueDays())
                .fullPaymentCutoffDays(policy.getFullPaymentCutoffDays())
                .cancellationPolicy(policy.getCancellationPolicy())
                .approvedBy(approver != null ? approver.getUserId() : null)
                .approvedByName(approver != null ? approver.getUsername() : null)
                .approvedAt(policy.getApprovedAt())
                .createdAt(policy.getCreatedAt())
                .updatedAt(policy.getUpdatedAt())
                .build();
    }
}
