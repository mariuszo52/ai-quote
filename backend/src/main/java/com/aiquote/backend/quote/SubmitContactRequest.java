package com.aiquote.backend.quote;

import jakarta.validation.constraints.NotBlank;

public record SubmitContactRequest(
        @NotBlank(message = "Imię i nazwisko jest wymagane") String name,
        @NotBlank(message = "Telefon jest wymagany") String phone,
        String email) {
}
