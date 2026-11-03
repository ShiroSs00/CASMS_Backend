package com.casms.cruisepackage.service;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;

/** Serialize existing structure writes with mapping confirmation/Open Sale. No renderer/import implementation changes. */
@Aspect @Component @Order(0) @RequiredArgsConstructor
public class CruisePackageMappingMutationGuard {
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactions;
    @Around("execution(public * com.casms.ship.service.CabinService.create(..)) || execution(public * com.casms.ship.service.CabinService.update(..)) || "
            +"execution(public * com.casms.ship.service.CabinService.deleteUnused(..)) || execution(public * com.casms.ship.service.CabinService.changeOperationalStatus(..)) || "
            +"execution(public * com.casms.ship.service.CabinService.blockCabin(..)) || execution(public * com.casms.ship.service.CabinService.releaseCabin(..)) || "
            +"execution(public * com.casms.ship.service.DeckService.create(..)) || execution(public * com.casms.ship.service.DeckService.update(..)) || execution(public * com.casms.ship.service.DeckService.deleteUnused(..))")
    public Object structureWrite(ProceedingJoinPoint call) throws Throwable {
        try{return new TransactionTemplate(transactions).execute(tx->{
            UUID shipId;
            if(call.getArgs()[0] instanceof com.casms.ship.dto.request.CabinCreateRequest input)
                shipId=findShip("Deck","deck_id",input.getDeckId());
            else if(call.getArgs()[0] instanceof com.casms.ship.dto.request.DeckCreateRequest input)shipId=input.getShipId();
            else {
                String table=call.getSignature().getDeclaringTypeName().endsWith("CabinService")?"Cabin":"Deck";
                shipId=findShip(table,table.toLowerCase(java.util.Locale.ROOT)+"_id",call.getArgs()[0]);
            }
            if(shipId!=null)jdbc.queryForList("SELECT ship_id FROM \"Ship\" WHERE ship_id=? FOR UPDATE",UUID.class,shipId);
            try{return call.proceed();}catch(Throwable failure){throw new InvocationFailure(failure);}
        });}catch(InvocationFailure failure){throw failure.getCause();}
    }
    private UUID findShip(String table,String column,Object id){
        var rows=jdbc.queryForList("SELECT ship_id FROM \""+table+"\" WHERE "+column+"=?",UUID.class,id);
        return rows.isEmpty()?null:rows.getFirst(); // Existing service retains its own not-found/error contract.
    }
    private static final class InvocationFailure extends RuntimeException {InvocationFailure(Throwable cause){super(cause);}}
}
