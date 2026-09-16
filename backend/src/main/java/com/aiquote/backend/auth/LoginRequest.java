package com.aiquote.backend.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Email jest wymagany") String email,
        @NotBlank(message = "Hasło jest wymagane") String password) {
}
