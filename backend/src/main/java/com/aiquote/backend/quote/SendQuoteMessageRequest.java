package com.aiquote.backend.quote;

import jakarta.validation.constraints.NotBlank;

public record SendQuoteMessageRequest(@NotBlank(message = "Treść wiadomości jest wymagana") String content) {
}
