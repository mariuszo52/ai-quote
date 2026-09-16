package com.aiquote.backend.feedback;

import com.aiquote.backend.quote.QuoteLineItem;
import java.time.Instant;
import java.util.List;

public record QuoteFeedbackResponse(
        Long id,
        Long quoteId,
        double aiTotal,
        double finalTotal,
        double diffAmount,
        Double diffPercentage,
        List<QuoteLineItem> aiItems,
        List<QuoteLineItem> finalItems,
        List<ChangedItem> changedItems,
        List<QuoteLineItem> addedItems,
        List<QuoteLineItem> removedItems,
        String reason,
        String note,
        Instant reasonSubmittedAt,
        Instant createdAt) {
}
