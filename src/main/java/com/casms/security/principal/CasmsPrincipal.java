package com.casms.security.principal;

import com.casms.identity.entity.User;
import com.casms.identity.enums.Permission;
import com.casms.identity.enums.Role;
import com.casms.identity.repository.RolePermissionRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Collections;
import java.util.stream.Stream;

@Getter
@RequiredArgsConstructor
public class CasmsPrincipal implements UserDetails {

    private final User user;
    private final RolePermissionRepository rolePermissionRepository;

    public UUID getUserId() {
        return user.getUserId();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Set<GrantedAuthority> authorities = new HashSet<>();
        if (user.getRoles() != null) {
            for (Role role : user.getRoles()) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
                authorities.add(new SimpleGrantedAuthority(role.name()));
            }
        }
        if (user.getRoles() != null && rolePermissionRepository != null) {
            for (Role role : user.getRoles()) {
                rolePermissionRepository.findByRole(role).ifPresent(rp -> {
                    if (rp.getPermissions() != null) {
                        for (Permission p : rp.getPermissions()) {
                            authorities.add(new SimpleGrantedAuthority(p.name()));
                        }
                    }
                });
            }
        }
        return authorities;
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isAccountNonLocked() {
        return user.getStatus() != com.casms.identity.enums.UserStatus.LOCKED;
    }

    @Override
    public boolean isEnabled() {
        return user.getStatus() == com.casms.identity.enums.UserStatus.ACTIVE;
    }

    public boolean hasRole(Role role) {
        return user.hasRole(role);
    }

    public boolean hasAnyRole(Role... roles) {
        return user.hasAnyRole(roles);
    }

    public boolean hasAllRoles(Role... roles) {
        return user.hasAllRoles(roles);
    }

    public boolean hasPermission(Permission permission) {
        return user.getRoles().stream()
                .flatMap(role -> {
                    var rpOptional = rolePermissionRepository.findByRole(role);
                    return rpOptional.map(rp -> rp.getPermissions().stream())
                            .orElseGet(Stream::empty);
                })
                .anyMatch(permission::equals);
    }

    public boolean hasAnyPermission(Permission... permissions) {
        for (Permission perm : permissions) {
            if (hasPermission(perm)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasAllPermissions(Permission... permissions) {
        for (Permission perm : permissions) {
            if (!hasPermission(perm)) {
                return false;
            }
        }
        return true;
    }

    public boolean isAdmin() {
        return user.isAdmin();
    }

    public boolean isStaff() {
        return user.isStaff();
    }
}
