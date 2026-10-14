package com.casms.security.authorization;

import com.casms.identity.enums.Permission;
import com.casms.identity.repository.RolePermissionRepository;
import com.casms.security.principal.CasmsPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Component("casmsPermissionEvaluator")
public class CasmsPermissionEvaluator implements org.springframework.security.access.PermissionEvaluator {

    private final RolePermissionRepository rolePermissionRepository;

    public CasmsPermissionEvaluator(RolePermissionRepository rolePermissionRepository) {
        this.rolePermissionRepository = rolePermissionRepository;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        if (!(authentication.getPrincipal() instanceof CasmsPrincipal principal)) {
            return false;
        }

        if (permission == null) {
            return false;
        }

        String permissionName = permission.toString();

        try {
            Permission perm = Permission.valueOf(permissionName);
            return principal.hasPermission(perm);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType, Object permission) {
        return hasPermission(authentication, null, permission);
    }
}