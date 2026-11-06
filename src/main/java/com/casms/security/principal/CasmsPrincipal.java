package com.casms.security.principal;

import com.casms.identity.entity.User;
import com.casms.identity.enums.Permission;
import com.casms.identity.enums.Role;
import com.casms.identity.enums.UserStatus;
import com.casms.identity.repository.RolePermissionRepository;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@Getter
@RequiredArgsConstructor
public class CasmsPrincipal implements UserDetails {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient User user;
    private final transient RolePermissionRepository rolePermissionRepository;

    public UUID getUserId() {
        return user.getUserId();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (user.hasRole(Role.POS)) {
            return Set.of(
                    new SimpleGrantedAuthority("ROLE_POS"), new SimpleGrantedAuthority("POS"));
        }
        Set<GrantedAuthority> authorities = new HashSet<>();
        addRoleAuthorities(authorities);
        addPermissionAuthorities(authorities);
        return authorities;
    }

    private void addRoleAuthorities(Set<GrantedAuthority> authorities) {
        if (user.getRoles() == null) {
            return;
        }
        for (Role role : user.getRoles()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
            authorities.add(new SimpleGrantedAuthority(role.name()));
        }
    }

    private void addPermissionAuthorities(Set<GrantedAuthority> authorities) {
        if (user.getRoles() == null || rolePermissionRepository == null) {
            return;
        }
        for (Role role : user.getRoles()) {
            rolePermissionRepository
                    .findByRole(role)
                    .map(com.casms.identity.entity.RolePermission::getPermissions)
                    .ifPresent(perms -> perms.forEach(p -> authorities.add(new SimpleGrantedAuthority(p.name()))));
        }
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
        return user.getStatus() != UserStatus.LOCKED;
    }

    @Override
    public boolean isEnabled() {
        return user.getStatus() == UserStatus.ACTIVE;
    }

    public boolean hasRole(Role role) {
        if (user.hasRole(Role.POS)) return role == Role.POS;
        return user.hasRole(role);
    }

    public boolean hasAnyRole(Role... roles) {
        return java.util.Arrays.stream(roles).anyMatch(this::hasRole);
    }

    public boolean hasAllRoles(Role... roles) {
        return java.util.Arrays.stream(roles).allMatch(this::hasRole);
    }

    public boolean hasPermission(Permission permission) {
        if (user.hasRole(Role.POS)) return false;
        return user.getRoles().stream()
                .flatMap(
                        role -> {
                            var rpOptional = rolePermissionRepository.findByRole(role);
                            return rpOptional
                                    .map(rp -> rp.getPermissions().stream())
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

    public boolean hasAuthority(String authority) {
        if (authority == null) {
            return false;
        }
        return getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equalsIgnoreCase(authority));
    }
}
