package com.casms.config;

import com.casms.identity.repository.RolePermissionRepository;
import com.casms.security.authorization.CasmsPermissionEvaluator;
import com.casms.security.jwt.JwtAuthenticationFilter;

import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.Http403ForbiddenEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RolePermissionRepository rolePermissionRepository;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        RequestMatcher bearerTokenRequest =
                request -> {
                    String authHeader = request.getHeader("Authorization");
                    return authHeader != null && authHeader.startsWith("Bearer ");
                };
        http.cors(Customizer.withDefaults())
                .csrf(
                        csrf ->
                                csrf.ignoringRequestMatchers(
                                        new AntPathRequestMatcher("/auth/**"),
                                        new AntPathRequestMatcher("/booking/**"),
                                        new AntPathRequestMatcher("/payment/sepay/**"),
                                        new AntPathRequestMatcher("/payment/guest/**"),
                                        new AntPathRequestMatcher("/h2-console/**"),
                                        new AntPathRequestMatcher("/v3/api-docs/**"),
                                        new AntPathRequestMatcher("/swagger-ui/**"),
                                        new AntPathRequestMatcher("/swagger-ui.html"),
                                        bearerTokenRequest))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(
                        errors -> {
                            var forbidden = new Http403ForbiddenEntryPoint();
                            errors.authenticationEntryPoint((request, response, exception) -> {
                                if (bearerTokenRequest.matches(request)) {
                                    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
                                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                                } else {
                                    forbidden.commence(request, response, exception);
                                }
                            });
                        })
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(
                                                "/v3/api-docs/**",
                                                "/v3/api-docs.yaml",
                                                "/swagger-ui/**",
                                                "/swagger-ui.html",
                                                "/swagger-resources/**",
                                                "/webjars/**",
                                                "/auth/**",
                                                "/h2-console/**",
                                                "/booking/available-cabins",
                                                "/booking/3d-available-cabins",
                                                "/booking/lookup",
                                                "/booking/lookup/**",
                                                "/booking/recovery",
                                                "/payment/sepay/**",
                                                "/payment/guest/**")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/booking/quotes/**",
                                                "/cruise",
                                                "/cruise/**",
                                                "/destinations",
                                                "/destinations/**",
                                                "/ports",
                                                "/ports/**",
                                                "/ships/**",
                                                "/itineraries",
                                                "/itineraries/**",
                                                "/cruise-stops/**",
                                                "/decks/**",
                                                "/cabins/**",
                                                "/servicepackage/packages",
                                                "/servicepackage/packages/**")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.POST, "/booking", "/booking/quotes")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .addFilterBefore(
                        jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PermissionEvaluator permissionEvaluator() {
        return new CasmsPermissionEvaluator(rolePermissionRepository);
    }
}
