package com.aiquote.backend.quote;

import java.time.Instant;

/**
 * One row of the leads panel — a Lead joined with its Quote (if one exists yet). Lives
 * in the quote package, not lead, because building it requires depending on Quote; see
 * LeadOverviewController's javadoc for the full reasoning.
 */
public record LeadOverviewResponse(
        Long leadId,
        String clientName,
        String clientPhone,
        String clientEmail,
        String description,
        String leadStatus,
        Instant createdAt,
        Long quoteId,
        String quoteStatus,
        Double quoteTotal,
        boolean awaitingApproval) {
}
