package com.aiquote.backend.tenant;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Reads the current tenant (company) and user from the Spring Security context,
 * populated per-request by JwtAuthenticationFilter. Every service touching
 * tenant-owned data must scope its repository calls through the companyId
 * obtained here instead of trusting client-supplied identifiers.
 */
public final class TenantContext {

    private TenantContext() {
    }

    public static AuthenticatedUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new IllegalStateException("No authenticated tenant user in the security context");
        }
        return user;
    }

    public static Long currentCompanyId() {
        return currentUser().companyId();
    }
}
