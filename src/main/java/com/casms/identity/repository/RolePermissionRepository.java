package com.casms.identity.repository;

import com.casms.identity.entity.RolePermission;
import com.casms.identity.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, Role> {

    Optional<RolePermission> findByRole(Role role);
}