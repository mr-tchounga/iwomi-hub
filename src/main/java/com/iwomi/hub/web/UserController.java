package com.iwomi.hub.web;

import com.iwomi.hub.config.AuthUser;
import com.iwomi.hub.config.CurrentUser;
import com.iwomi.hub.domain.AppUser;
import com.iwomi.hub.domain.Enums.Role;
import com.iwomi.hub.repo.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** User administration (ADMIN only; enforced in SecurityConfig). */
@RestController
@RequestMapping("/api/users")
public class UserController {

    public record UserRequest(String username, String password, String fullName, String email, Role role,
                              Long employeeId, Long partnerId, Boolean active) {}

    private final AppUserRepository users;
    private final PasswordEncoder encoder;

    public UserController(AppUserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @GetMapping
    public List<UserView> list() {
        return users.findAll().stream().map(UserView::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserView create(@RequestBody UserRequest r) {
        if (r.username() == null || !r.username().trim().matches("[A-Za-z0-9._-]{3,40}")) {
            throw new IllegalArgumentException("Username must be 3-40 characters: letters, digits, dot, dash or underscore");
        }
        if (r.role() == null) throw new IllegalArgumentException("Role is required");
        if (r.password() == null || r.password().length() < 8) throw new IllegalArgumentException("Password must have at least 8 characters");
        if (users.existsByUsernameIgnoreCase(r.username().trim())) throw new IllegalArgumentException("This username is already taken");
        AppUser u = new AppUser();
        u.setUsername(r.username().trim());
        u.setPasswordHash(encoder.encode(r.password()));
        apply(u, r);
        return UserView.of(users.save(u));
    }

    @PutMapping("/{id}")
    public UserView update(@PathVariable Long id, @RequestBody UserRequest r) {
        AppUser u = users.findById(id).orElseThrow(() -> new NotFoundException("User not found: " + id));
        AuthUser me = CurrentUser.require();
        boolean self = me.id().equals(id);
        if (self && ((r.role() != null && r.role() != Role.ADMIN) || Boolean.FALSE.equals(r.active()))) {
            throw new IllegalArgumentException("You cannot remove your own admin access");
        }
        if (r.password() != null && !r.password().isBlank()) {
            if (r.password().length() < 8) throw new IllegalArgumentException("Password must have at least 8 characters");
            u.setPasswordHash(encoder.encode(r.password()));
        }
        apply(u, r);
        return UserView.of(users.save(u));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        if (CurrentUser.require().id().equals(id)) throw new IllegalArgumentException("You cannot delete your own account");
        if (!users.existsById(id)) throw new NotFoundException("User not found: " + id);
        users.deleteById(id);
    }

    private void apply(AppUser u, UserRequest r) {
        u.setFullName(r.fullName());
        u.setEmail(r.email());
        if (r.role() != null) u.setRole(r.role());
        u.setEmployeeId(r.employeeId());
        u.setPartnerId(r.partnerId());
        if (r.active() != null) u.setActive(r.active());
    }
}
