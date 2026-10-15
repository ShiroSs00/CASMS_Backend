package com.casms.identity.service;

import com.casms.common.exception.BusinessException;
import com.casms.identity.dto.LoginRequest;
import com.casms.identity.dto.LoginResponse;
import com.casms.identity.entity.User;
import com.casms.identity.repository.UserRepository;
import com.casms.security.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        log.info("Login attempt for username: {}", request.getUsername());

        // Authenticate using Spring Security
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
        } catch (BadCredentialsException e) {
            log.warn("Failed login attempt for username: {}", request.getUsername());
            throw new BusinessException("INVALID_CREDENTIALS", "Invalid username or password");
        }

        // Get user details
        var principal = (com.casms.security.principal.CasmsPrincipal) authentication.getPrincipal();
        User user = principal.getUser();

        // Check account status
        if (user.getStatus() != com.casms.identity.enums.UserStatus.ACTIVE) {
            throw new BusinessException("ACCOUNT_DISABLED", "Account is disabled or locked");
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

        log.info("User {} logged in successfully", user.getUsername());
        return response;
    }

    @Transactional(readOnly = true)
    public LoginResponse refreshToken(String accessToken) {
        // Extract user ID from token
        UUID userId = jwtService.extractUserId(accessToken);

        // Verify token is valid
        if (!jwtService.isValid(accessToken)) {
            throw new BusinessException("INVALID_TOKEN", "Invalid or expired token");
        }

        // Get user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found"));

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

        return response;
    }
}