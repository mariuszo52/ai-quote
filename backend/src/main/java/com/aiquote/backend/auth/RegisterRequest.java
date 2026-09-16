package com.aiquote.backend.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Nazwa firmy jest wymagana") String companyName,
        @NotBlank(message = "Email jest wymagany") @Email(message = "Nieprawidłowy adres email") String email,
        @NotBlank(message = "Hasło jest wymagane") @Size(min = 8, message = "Hasło musi mieć co najmniej 8 znaków") String password) {
}
