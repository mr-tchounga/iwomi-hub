package com.iwomi.hub.config;

import com.iwomi.hub.domain.Enums.Role;

/** The authenticated person behind a request (principal in the security context). */
public record AuthUser(Long id, String username, String fullName, Role role, Long employeeId, Long partnerId) {

    public boolean is(Role... roles) {
        for (Role r : roles) {
            if (r == role) return true;
        }
        return false;
    }
}
