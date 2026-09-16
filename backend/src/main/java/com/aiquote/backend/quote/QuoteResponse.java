package com.aiquote.backend.quote;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record QuoteResponse(
        Long id,
        Long companyId,
        Long conversationId,
        Long leadId,
        String status,
        String currency,
        List<QuoteLineItem> items,
        double subtotal,
        double total,
        Double aiConfidence,
        String aiReasoning,
        List<String> uncertainFactors,
        List<QuoteLineItem> aiItems,
        double aiSubtotal,
        double aiTotal,
        String clientNote,
        String internalNote,
        String estimatedTimeline,
        LocalDate offerValidUntil,
        Instant approvedAt,
        List<QuoteChangeLogResponse> changeLog,
        Instant createdAt,
        Instant updatedAt,
        String clientName,
        String clientPhone,
        String clientEmail) {
}
