package com.aiquote.backend.auth;

public record AuthResponse(String token, Long companyId, String companySlug) {
}
