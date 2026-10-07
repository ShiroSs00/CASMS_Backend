package com.casms.audit;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * Skeleton AuditAspect.
 * Business-specific pointcuts should be added when audit requirements are defined.
 */
@Slf4j
@Aspect
@Component
public class AuditAspect {

    @Around("@annotation(com.casms.audit.Auditable)")
    public Object audit(ProceedingJoinPoint pjp) throws Throwable {
        log.debug("Audit: {}", pjp.getSignature());
        return pjp.proceed();
    }
}
