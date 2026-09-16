package com.aiquote.backend.quote;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Produces a short, human-readable line describing what an owner's edit changed —
 * matched by item name (trimmed, case-insensitive), same convention as
 * PricingProfileMerger. Deliberately compares only totalPrice per item (not every
 * field) so the log reads like "Malowanie: 2400 PLN -> 2800 PLN" rather than a noisy
 * field-by-field diff.
 */
public final class QuoteChangeSummaryBuilder {

    private QuoteChangeSummaryBuilder() {
    }

    /** Empty string when nothing meaningfully changed — caller should skip logging in that case. */
    public static String build(List<QuoteLineItem> before, List<QuoteLineItem> after, String currency) {
        List<String> lines = new ArrayList<>();

        for (QuoteLineItem beforeItem : before) {
            QuoteLineItem afterItem = findByName(after, beforeItem.name());
            if (afterItem == null) {
                lines.add(beforeItem.name() + ": usunięto");
            } else if (!Objects.equals(beforeItem.totalPrice(), afterItem.totalPrice())) {
                lines.add(beforeItem.name() + ": " + formatPrice(beforeItem.totalPrice(), currency) + " → " + formatPrice(afterItem.totalPrice(), currency));
            }
        }

        for (QuoteLineItem afterItem : after) {
            if (findByName(before, afterItem.name()) == null) {
                lines.add("Nowa pozycja: " + afterItem.name() + " — " + formatPrice(afterItem.totalPrice(), currency));
            }
        }

        return String.join("; ", lines);
    }

    private static QuoteLineItem findByName(List<QuoteLineItem> items, String name) {
        String key = normalize(name);
        return items.stream().filter(item -> normalize(item.name()).equals(key)).findFirst().orElse(null);
    }

    private static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private static String formatPrice(Double price, String currency) {
        return price == null ? "brak ceny" : price + " " + currency;
    }
}
