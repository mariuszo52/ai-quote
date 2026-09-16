package com.aiquote.backend.quote;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record UpdateQuoteRequest(
        @NotNull(message = "Lista pozycji jest wymagana") @Valid List<QuoteLineItem> items,
        String clientNote,
        String internalNote,
        String estimatedTimeline,
        LocalDate offerValidUntil) {
}
