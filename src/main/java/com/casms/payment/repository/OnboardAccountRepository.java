package com.casms.payment.repository;

import com.casms.payment.entity.OnboardAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface OnboardAccountRepository extends JpaRepository<OnboardAccount, UUID> {
}
