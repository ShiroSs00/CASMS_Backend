package com.casms.cruisepackage.repository;

import com.casms.cruisepackage.entity.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

/** Aggregate persistence; callers own the transaction and lock Cruise before an Offer. */
@Repository @RequiredArgsConstructor
public class CruisePackageRepository {
    private final EntityManager em;
    public <T> T find(Class<T> type, UUID id, boolean lock) {
        return lock ? em.find(type,id,LockModeType.PESSIMISTIC_WRITE) : em.find(type,id);
    }
    public void insert(Object entity) { em.persist(entity); }
    public void flush() { em.flush(); }
    public void increment(Object entity) { em.lock(entity,LockModeType.OPTIMISTIC_FORCE_INCREMENT); }
    public List<CruisePackageTemplate> templates() {
        return em.createQuery("from CruisePackageTemplate order by createdAt desc, id",CruisePackageTemplate.class).getResultList();
    }
    public List<CruisePackageTemplateVersion> versions(UUID templateId) {
        return em.createQuery("from CruisePackageTemplateVersion where templateId=:id order by versionNumber desc",CruisePackageTemplateVersion.class)
                .setParameter("id",templateId).getResultList();
    }
    public List<CruisePackageBenefit> benefits(UUID versionId) {
        return em.createQuery("from CruisePackageBenefit where templateVersionId=:id order by id",CruisePackageBenefit.class)
                .setParameter("id",versionId).getResultList();
    }
    public List<DepartureCruisePackage> offers(UUID cruiseId) {
        return em.createQuery("from DepartureCruisePackage where cruiseId=:id order by createdAt,id",DepartureCruisePackage.class)
                .setParameter("id",cruiseId).getResultList();
    }
    public List<DepartureCruisePackagePrice> prices(UUID offerId) {
        return em.createQuery("from DepartureCruisePackagePrice where offerId=:id order by revisionNumber desc",DepartureCruisePackagePrice.class)
                .setParameter("id",offerId).getResultList();
    }
    public List<DepartureCruisePackageBenefit> bindings(UUID offerId) {
        return em.createQuery("from DepartureCruisePackageBenefit where offerId=:id order by id",DepartureCruisePackageBenefit.class)
                .setParameter("id",offerId).getResultList();
    }
    public List<BookingCruisePackageBenefitSnapshot> purchasedBenefits(UUID passengerId) {
        return em.createQuery("from BookingCruisePackageBenefitSnapshot where bookingPassengerId=:id order by id",BookingCruisePackageBenefitSnapshot.class)
                .setParameter("id",passengerId).getResultList();
    }
}
