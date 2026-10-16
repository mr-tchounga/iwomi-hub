package com.iwomi.hub.web;

import com.iwomi.hub.domain.AppUser;
import com.iwomi.hub.domain.Enums.Role;

/** Safe representation of a user (never includes the password hash). */
public record UserView(Long id, String username, String fullName, String email, Role role,
                       Long employeeId, Long partnerId, boolean active) {

    public static UserView of(AppUser u) {
        return new UserView(u.getId(), u.getUsername(), u.getFullName(), u.getEmail(), u.getRole(),
                u.getEmployeeId(), u.getPartnerId(), u.isActive());
    }
}
