package com.aiquote.backend.feedback;

/** One line item whose price the owner adjusted between AI's draft and the final
 * accepted version — matched by normalized name, same convention as
 * quote.QuoteChangeSummaryBuilder. */
public record ChangedItem(
        String name,
        Double aiQuantity,
        Double aiUnitPrice,
        Double aiTotalPrice,
        Double finalQuantity,
        Double finalUnitPrice,
        Double finalTotalPrice) {
}
