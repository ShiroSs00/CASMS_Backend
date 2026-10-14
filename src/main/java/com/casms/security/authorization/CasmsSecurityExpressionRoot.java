package com.casms.security.authorization;

import com.casms.identity.enums.Permission;
import com.casms.identity.enums.Role;
import com.casms.security.principal.CasmsPrincipal;
import org.springframework.security.access.expression.SecurityExpressionRoot;
import org.springframework.security.access.expression.method.MethodSecurityExpressionOperations;
import org.springframework.security.core.Authentication;

import java.io.Serializable;
import java.util.Set;
import java.util.UUID;

public class CasmsSecurityExpressionRoot extends SecurityExpressionRoot implements MethodSecurityExpressionOperations {

    private final CasmsPrincipal principal;
    private Object filterObject;
    private Object returnObject;
    private Object targetObject;

    public CasmsSecurityExpressionRoot(Authentication authentication) {
        super(authentication);
        if (authentication != null && authentication.getPrincipal() instanceof CasmsPrincipal) {
            this.principal = (CasmsPrincipal) authentication.getPrincipal();
        } else {
            this.principal = null;
        }
    }

    public boolean hasRole(Role role) {
        return principal != null && principal.hasRole(role);
    }

    public boolean hasAnyRole(Role... roles) {
        return principal != null && principal.hasAnyRole(roles);
    }

    public boolean hasAllRoles(Role... roles) {
        return principal != null && principal.hasAllRoles(roles);
    }

    public boolean hasPermission(Permission permission) {
        return principal != null && principal.hasPermission(permission);
    }

    public boolean hasAnyPermission(Permission... permissions) {
        if (principal == null) return false;
        for (Permission perm : permissions) {
            if (principal.hasPermission(perm)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasAllPermissions(Permission... permissions) {
        if (principal == null) return false;
        for (Permission perm : permissions) {
            if (!principal.hasPermission(perm)) {
                return false;
            }
        }
        return true;
    }

    public boolean isAdmin() {
        return principal != null && principal.isAdmin();
    }

    public boolean isStaff() {
        return principal != null && principal.isStaff();
    }

    public UUID getCurrentUserId() {
        return principal != null ? principal.getUserId() : null;
    }

    public void setFilterObject(Object filterObject) {
        this.filterObject = filterObject;
    }

    public Object getFilterObject() {
        return filterObject;
    }

    public void setReturnObject(Object returnObject) {
        this.returnObject = returnObject;
    }

    public Object getReturnObject() {
        return returnObject;
    }

    public void setTargetObject(Object targetObject) {
        this.targetObject = targetObject;
    }

    public Object getTargetObject() {
        return targetObject;
    }

    @Override
    public Object getThis() {
        return this;
    }
}