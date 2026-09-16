package com.aiquote.backend.quote;

import java.util.List;
import java.util.Objects;

/**
 * Deterministic price arithmetic, deliberately not delegated to the AI: totalPrice per
 * item and the quote's subtotal/total are always computed here from quantity *
 * unitPrice, never trusted verbatim from a model response. An item with an unknown
 * unitPrice contributes nothing to the totals rather than a guessed number.
 */
public final class QuoteCalculator {

    private QuoteCalculator() {
    }

    /** Returns a copy of the item with totalPrice recomputed from quantity * unitPrice. */
    public static QuoteLineItem withComputedTotal(QuoteLineItem item) {
        return new QuoteLineItem(
                item.name(), item.description(), item.quantity(), item.unit(), item.unitPrice(), itemTotal(item), item.source());
    }

    /** Null when unitPrice is unknown — never a guessed number. */
    public static Double itemTotal(QuoteLineItem item) {
        if (item.unitPrice() == null) {
            return null;
        }
        double quantity = item.quantity() != null ? item.quantity() : 1.0;
        return round(quantity * item.unitPrice());
    }

    public static double subtotal(List<QuoteLineItem> items) {
        return round(items.stream()
                .map(QuoteCalculator::itemTotal)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum());
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
