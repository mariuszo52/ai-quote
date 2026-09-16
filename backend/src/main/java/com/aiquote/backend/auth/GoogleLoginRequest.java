package com.aiquote.backend.auth;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(@NotBlank(message = "Token Google jest wymagany") String idToken) {
}
