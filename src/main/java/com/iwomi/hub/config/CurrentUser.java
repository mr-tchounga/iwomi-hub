package com.iwomi.hub.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {
    private CurrentUser() {}

    /** The logged-in user, or null for anonymous visitors and API-key apps. */
    public static AuthUser get() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.getPrincipal() instanceof AuthUser u) ? u : null;
    }

    public static AuthUser require() {
        AuthUser u = get();
        if (u == null) throw new IllegalStateException("Not authenticated");
        return u;
    }
}
