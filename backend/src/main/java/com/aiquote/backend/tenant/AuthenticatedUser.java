package com.aiquote.backend.tenant;

public record AuthenticatedUser(Long userId, Long companyId, UserRole role) {
}
