package com.aiquote.backend.feedback;

import com.aiquote.backend.quote.QuoteLineItem;
import java.util.List;

/** The structured result of comparing an AI-drafted item list against the final
 * accepted one — everything QuoteFeedbackService needs to persist a QuoteFeedback row. */
public record QuoteFeedbackDiff(
        double diffAmount,
        Double diffPercentage,
        List<ChangedItem> changedItems,
        List<QuoteLineItem> addedItems,
        List<QuoteLineItem> removedItems) {
}
