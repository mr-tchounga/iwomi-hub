package com.iwomi.hub.config;

import com.iwomi.hub.repo.AppClientRepository;
import com.iwomi.hub.repo.AppUserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Who can do what.
 *  - Anonymous visitors: public catalog, help request, AI assistant (/api/portal/**), login/register.
 *  - EXTERNAL users: their own support requests (/api/me/**).
 *  - Staff roles (ADMIN, CTO, HR, ACCOUNTANT, EMPLOYEE): the rules below.
 *  - IWOMI apps: X-API-Key on /api/public/**.
 * Anything not listed under /api is ADMIN only.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] STAFF = {"ADMIN", "CTO", "HR", "ACCOUNTANT", "EMPLOYEE"};

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, AppClientRepository clients, AppUserRepository users,
                                    TokenService tokens) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/demo-accounts").permitAll()
                .requestMatchers("/api/portal/**").permitAll()
                .requestMatchers("/api/public/**").hasRole("APP")
                .requestMatchers("/api/auth/me").authenticated()
                .requestMatchers("/api/me/**").hasRole("EXTERNAL")
                .requestMatchers("/api/users/**").hasRole("ADMIN")
                .requestMatchers("/api/settings/**", "/api/routing-rules/**", "/api/app-clients/**").hasAnyRole("ADMIN", "CTO")
                .requestMatchers(HttpMethod.GET, "/api/projects/**", "/api/components/**").hasAnyRole(STAFF)
                .requestMatchers("/api/projects/**", "/api/components/**").hasAnyRole("ADMIN", "CTO")
                .requestMatchers(HttpMethod.GET, "/api/partners/**").hasAnyRole(STAFF)
                .requestMatchers("/api/partners/**").hasAnyRole("ADMIN", "CTO", "ACCOUNTANT")
                .requestMatchers(HttpMethod.GET, "/api/sales/**", "/api/kyc/**", "/api/kyc-documents/**")
                    .hasAnyRole("ADMIN", "CTO", "ACCOUNTANT")
                .requestMatchers("/api/sales/**", "/api/kyc/**", "/api/kyc-documents/**").hasAnyRole("ADMIN", "ACCOUNTANT")
                .requestMatchers(HttpMethod.GET, "/api/employees/**", "/api/departments/**").hasAnyRole(STAFF)
                .requestMatchers("/api/employees/**", "/api/departments/**").hasAnyRole("ADMIN", "HR")
                .requestMatchers("/api/tickets/**", "/api/notifications/**").hasAnyRole("ADMIN", "CTO", "EMPLOYEE")
                .requestMatchers(HttpMethod.GET, "/api/tasks/**").hasAnyRole("ADMIN", "CTO", "HR", "EMPLOYEE")
                .requestMatchers("/api/tasks/**").hasAnyRole("ADMIN", "CTO", "EMPLOYEE")
                .requestMatchers("/api/accelerator/**").hasAnyRole("ADMIN", "CTO", "EMPLOYEE")
                .requestMatchers("/api/dashboard/**", "/api/chat/**").hasAnyRole(STAFF)
                .requestMatchers("/api/**").hasRole("ADMIN")
                .anyRequest().permitAll())
            .addFilterBefore(new ApiKeyFilter(clients), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new TokenFilter(tokens, users), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
