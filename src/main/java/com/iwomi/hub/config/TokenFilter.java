package com.iwomi.hub.config;

import com.iwomi.hub.domain.AppUser;
import com.iwomi.hub.repo.AppUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/** Reads "Authorization: Bearer token" (or ?access_token= for the live notification stream). */
public class TokenFilter extends OncePerRequestFilter {

    private final TokenService tokens;
    private final AppUserRepository users;
    private final RequestAttributeSecurityContextRepository repository = new RequestAttributeSecurityContextRepository();

    public TokenFilter(TokenService tokens, AppUserRepository users) {
        this.tokens = tokens;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = null;
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            token = header.substring(7).trim();
        } else if ("/api/notifications/stream".equals(request.getRequestURI())) {
            token = request.getParameter("access_token");
        }
        if (token != null && !token.isEmpty()) {
            Optional<AppUser> user = tokens.verify(token).flatMap(users::findById).filter(AppUser::isActive);
            if (user.isPresent()) {
                AppUser u = user.get();
                AuthUser principal = new AuthUser(u.getId(), u.getUsername(), u.getFullName(), u.getRole(),
                        u.getEmployeeId(), u.getPartnerId());
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name())));
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(auth);
                SecurityContextHolder.setContext(context);
                repository.saveContext(context, request, response);
            }
        }
        chain.doFilter(request, response);
    }
}
