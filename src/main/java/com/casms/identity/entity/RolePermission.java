package com.casms.identity.entity;

import com.casms.identity.enums.Permission;
import com.casms.identity.enums.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"RolePermission\"")
public class RolePermission {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    @ElementCollection(targetClass = Permission.class, fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "\"RolePermissionMapping\"", joinColumns = @JoinColumn(name = "role"))
    @Column(name = "permission")
    private Set<Permission> permissions = new HashSet<>();

    public RolePermission() {}

    public RolePermission(Role role, Set<Permission> permissions) {
        this.role = role;
        setPermissions(permissions);
    }

    public void setPermissions(Set<Permission> permissions) {
        if (this.permissions == null) {
            this.permissions = new HashSet<>();
        } else {
            this.permissions.clear();
        }
        if (permissions != null) {
            this.permissions.addAll(permissions);
        }
    }

    public void addPermission(Permission permission) {
        this.permissions.add(permission);
    }

    public void removePermission(Permission permission) {
        this.permissions.remove(permission);
    }

    public boolean hasPermission(Permission permission) {
        return this.permissions.contains(permission);
    }

    public static RolePermission of(Role role, Permission... permissions) {
        RolePermission rp = new RolePermission();
        rp.setRole(role);
        rp.setPermissions(EnumSet.of(permissions[0], permissions));
        return rp;
    }
}