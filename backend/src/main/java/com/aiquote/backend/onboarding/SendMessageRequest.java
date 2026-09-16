package com.aiquote.backend.onboarding;

import jakarta.validation.constraints.NotBlank;

public record SendMessageRequest(@NotBlank(message = "Treść wiadomości jest wymagana") String content) {
}
