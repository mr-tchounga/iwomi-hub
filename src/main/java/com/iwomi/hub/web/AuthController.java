package com.iwomi.hub.web;

import com.iwomi.hub.config.AuthUser;
import com.iwomi.hub.config.CurrentUser;
import com.iwomi.hub.config.RateLimiter;
import com.iwomi.hub.config.TokenService;
import com.iwomi.hub.domain.AppUser;
import com.iwomi.hub.domain.Enums.Role;
import com.iwomi.hub.repo.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record LoginRequest(String username, String password) {}

    public record RegisterRequest(@NotBlank @Size(max = 200) String fullName,
                                  @NotBlank String username,
                                  @NotBlank @Size(min = 8, max = 100) String password,
                                  @Size(max = 200) String email) {}

    private static final List<String> DEMO_USERS = List.of("cto", "rh", "compta", "brice", "carine", "estelle", "david", "client");

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final RateLimiter limiter;
    private final boolean demo;
    private final String demoPassword;

    public AuthController(AppUserRepository users, PasswordEncoder encoder, TokenService tokens, RateLimiter limiter,
                          @Value("${hub.demo.enabled}") boolean demo, @Value("${hub.demo.password}") String demoPassword) {
        this.users = users;
        this.encoder = encoder;
        this.tokens = tokens;
        this.limiter = limiter;
        this.demo = demo;
        this.demoPassword = demoPassword;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest r, HttpServletRequest request) {
        if (!limiter.allow("login:" + RateLimiter.ip(request), 20, 900)) {
            throw new TooManyRequestsException("Too many login attempts. Please wait a few minutes.");
        }
        AppUser u = r.username() == null || r.password() == null ? null
                : users.findByUsernameIgnoreCase(r.username().trim()).orElse(null);
        if (u == null || !u.isActive() || !encoder.matches(r.password(), u.getPasswordHash())) {
            throw new UnauthorizedException("Invalid username or password");
        }
        return session(u);
    }

    /** Self-registration creates an EXTERNAL (customer) account only. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> register(@Valid @RequestBody RegisterRequest r, HttpServletRequest request) {
        if (!limiter.allow("register:" + RateLimiter.ip(request), 5, 3600)) {
            throw new TooManyRequestsException("Too many registrations from this address. Please try later.");
        }
        String username = r.username().trim();
        if (!username.matches("[A-Za-z0-9._-]{3,40}")) {
            throw new IllegalArgumentException("Username must be 3-40 characters: letters, digits, dot, dash or underscore");
        }
        if (users.existsByUsernameIgnoreCase(username)) throw new IllegalArgumentException("This username is already taken");
        AppUser u = new AppUser();
        u.setUsername(username);
        u.setPasswordHash(encoder.encode(r.password()));
        u.setFullName(r.fullName().trim());
        u.setEmail(r.email());
        u.setRole(Role.EXTERNAL);
        u.setActive(true);
        return session(users.save(u));
    }

    @GetMapping("/me")
    public UserView me() {
        AuthUser au = CurrentUser.require();
        return users.findById(au.id()).map(UserView::of).orElseThrow(() -> new UnauthorizedException("Session expired"));
    }

    /** Only in demo mode: lets people try each role with one click. Turn off with HUB_DEMO=false. */
    @GetMapping("/demo-accounts")
    public Map<String, Object> demoAccounts() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", demo);
        if (!demo) return out;
        out.put("password", demoPassword);
        out.put("accounts", users.findAll().stream()
                .filter(u -> DEMO_USERS.contains(u.getUsername()))
                .map(u -> Map.of("username", u.getUsername(), "role", u.getRole().name(), "fullName", String.valueOf(u.getFullName())))
                .toList());
        return out;
    }

    private Map<String, Object> session(AppUser u) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("token", tokens.issue(u.getId()));
        out.put("user", UserView.of(u));
        return out;
    }
}
