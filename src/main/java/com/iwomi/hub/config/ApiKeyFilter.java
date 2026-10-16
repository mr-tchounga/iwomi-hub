package com.iwomi.hub.config;

import com.iwomi.hub.domain.AppClient;
import com.iwomi.hub.repo.AppClientRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/** Authenticates IWOMI apps calling /api/public/** with an X-API-Key header. */
public class ApiKeyFilter extends OncePerRequestFilter {

    private final AppClientRepository clients;

    public ApiKeyFilter(AppClientRepository clients) {
        this.clients = clients;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/api/public/")) {
            String key = request.getHeader("X-API-Key");
            if (key != null && !key.isBlank()) {
                Optional<AppClient> client = clients.findByApiKeyAndActiveTrue(key.trim());
                if (client.isPresent()) {
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            client.get(), null, List.of(new SimpleGrantedAuthority("ROLE_APP")));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        }
        chain.doFilter(request, response);
    }
}
