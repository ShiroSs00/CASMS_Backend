package com.casms.identity.service;

import com.casms.common.exception.BusinessException;
import com.casms.identity.dto.LoginRequest;
import com.casms.identity.dto.LoginResponse;
import com.casms.identity.dto.UserMeResponse;
import com.casms.identity.entity.Passenger;
import com.casms.identity.entity.User;
import com.casms.identity.enums.Permission;
import com.casms.identity.enums.Role;
import com.casms.identity.enums.UserStatus;
import com.casms.identity.repository.PassengerRepository;
import com.casms.identity.repository.RolePermissionRepository;
import com.casms.identity.repository.UserRepository;
import com.casms.security.jwt.JwtService;
import com.casms.security.principal.CasmsPrincipal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PassengerRepository passengerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        // Authenticate using Spring Security
        Authentication authentication;
        try {
            authentication =
                    authenticationManager.authenticate(
                            new UsernamePasswordAuthenticationToken(
                                    request.getUsername(), request.getPassword()));
        } catch (BadCredentialsException | AccountStatusException | UsernameNotFoundException e) {
            throw invalidCredentials();
        } catch (AuthenticationServiceException e) {
            throw e;
        } catch (AuthenticationException | DataAccessException e) {
            throw new AuthenticationServiceException("Authentication provider failed", e);
        }

        // Get user details
        var principal = (CasmsPrincipal) authentication.getPrincipal();
        User user = principal.getUser();

        // Check account status
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw invalidCredentials();
        }

        // Update last login
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        // Generate JWT token
        String accessToken = jwtService.generateToken(user.getUsername(), user.getUserId());
        long expiresIn = jwtService.getExpirationMs() / 1000;

        // Build response
        LoginResponse response = new LoginResponse();
        response.setAccessToken(accessToken);
        response.setExpiresIn(expiresIn);
        response.setUserId(user.getUserId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setRoles(user.getRoles());
        response.setEffectivePermissions(getEffectivePermissions(user));

        log.info("User {} logged in successfully", user.getUsername());
        return response;
    }

    @Transactional(readOnly = true)
    public LoginResponse refreshToken(String accessToken) {
        User user =
                jwtService.findValidUserId(accessToken)
                        .flatMap(userRepository::findById)
                        .filter(account -> account.getStatus() == UserStatus.ACTIVE)
                        .orElseThrow(AuthService::invalidToken);

        // Generate new token
        String newAccessToken = jwtService.generateToken(user.getUsername(), user.getUserId());
        long expiresIn = jwtService.getExpirationMs() / 1000;

        LoginResponse response = new LoginResponse();
        response.setAccessToken(newAccessToken);
        response.setExpiresIn(expiresIn);
        response.setUserId(user.getUserId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setRoles(user.getRoles());
        response.setEffectivePermissions(getEffectivePermissions(user));

        return response;
    }

    private static BusinessException invalidCredentials() {
        return new BusinessException(
                HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid username or password");
    }

    private static BusinessException invalidToken() {
        return new BusinessException(
                HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Invalid or expired token");
    }

    public Set<String> getEffectivePermissions(User user) {
        if (user == null || user.hasRole(Role.POS)) {
            return Collections.emptySet();
        }
        Set<String> permissions = new TreeSet<>();
        if (user.getRoles() != null && rolePermissionRepository != null) {
            for (Role role : user.getRoles()) {
                rolePermissionRepository
                        .findByRole(role)
                        .ifPresent(
                                rp -> {
                                    if (rp.getPermissions() != null) {
                                        for (Permission p : rp.getPermissions()) {
                                            permissions.add(p.name());
                                        }
                                    }
                                });
            }
        }
        return permissions;
    }

    @Transactional(readOnly = true)
    public UserMeResponse getCurrentUser(CasmsPrincipal principal) {
        User freshUser =
                userRepository.findById(principal.getUserId())
                        .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                        .orElseThrow(
                                () -> new BusinessException(
                                        HttpStatus.UNAUTHORIZED,
                                        "AUTH_REQUIRED",
                                        "Yêu cầu đăng nhập để truy cập thông tin phiên"));
        UUID passengerId =
                (passengerRepository != null)
                        ? passengerRepository
                                .findByUser_UserId(freshUser.getUserId())
                                .map(Passenger::getPassengerId)
                                .orElse(null)
                        : null;
        return UserMeResponse.builder()
                .userId(freshUser.getUserId())
                .username(freshUser.getUsername())
                .email(freshUser.getEmail())
                .status(freshUser.getStatus())
                .roles(freshUser.getRoles())
                .effectivePermissions(getEffectivePermissions(freshUser))
                .passengerId(passengerId)
                .build();
    }
}
