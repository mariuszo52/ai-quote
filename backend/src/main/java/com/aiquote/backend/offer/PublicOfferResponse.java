package com.aiquote.backend.offer;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Everything the client is allowed to see at /offer/{publicToken} — and nothing else.
 * No quoteId, no companyId, no internal status/sentAt/lastSendError, no publicToken
 * (the client already has it, in the URL). See PublicOfferController's javadoc.
 */
public record PublicOfferResponse(
        Long offerNumber,
        String companyName,
        String companyContactEmail,
        String currency,
        List<OfferLineItem> items,
        double total,
        String clientName,
        String jobDescription,
        String estimatedTimeline,
        LocalDate validUntil,
        Instant createdAt) {
}
