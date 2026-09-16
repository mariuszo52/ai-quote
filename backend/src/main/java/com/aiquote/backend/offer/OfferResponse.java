package com.aiquote.backend.offer;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Owner-facing view of an offer — includes send-status fields (Etap 13), unlike PublicOfferResponse. */
public record OfferResponse(
        Long id,
        Long quoteId,
        String publicToken,
        String status,
        String currency,
        List<OfferLineItem> items,
        double total,
        String clientName,
        String clientPhone,
        String clientEmail,
        String jobDescription,
        String estimatedTimeline,
        LocalDate validUntil,
        Instant sentAt,
        String lastSendError,
        Instant createdAt) {
}
