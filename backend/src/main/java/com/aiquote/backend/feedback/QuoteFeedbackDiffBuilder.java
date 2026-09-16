package com.aiquote.backend.feedback;

import com.aiquote.backend.quote.QuoteLineItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Compares the AI's original item list against the final accepted one — matched by
 * normalized name, same convention as quote.QuoteChangeSummaryBuilder (which does the
 * same match for its human-readable per-edit log). This is deliberately a separate,
 * structured builder: QuoteChangeSummaryBuilder produces a text line for the change
 * log, this produces typed data meant to be stored and later analyzed (Etap 15's
 * "future learning" data layer — no ML here, just the structured comparison itself).
 */
public final class QuoteFeedbackDiffBuilder {

    private QuoteFeedbackDiffBuilder() {
    }

    public static QuoteFeedbackDiff build(List<QuoteLineItem> aiItems, List<QuoteLineItem> finalItems, double aiTotal, double finalTotal) {
        List<ChangedItem> changed = new ArrayList<>();
        List<QuoteLineItem> removed = new ArrayList<>();
        List<QuoteLineItem> added = new ArrayList<>();

        for (QuoteLineItem aiItem : aiItems) {
            QuoteLineItem finalItem = findByName(finalItems, aiItem.name());
            if (finalItem == null) {
                removed.add(aiItem);
            } else if (!Objects.equals(aiItem.unitPrice(), finalItem.unitPrice()) || !Objects.equals(aiItem.quantity(), finalItem.quantity())) {
                changed.add(new ChangedItem(
                        aiItem.name(),
                        aiItem.quantity(),
                        aiItem.unitPrice(),
                        aiItem.totalPrice(),
                        finalItem.quantity(),
                        finalItem.unitPrice(),
                        finalItem.totalPrice()));
            }
        }

        for (QuoteLineItem finalItem : finalItems) {
            if (findByName(aiItems, finalItem.name()) == null) {
                added.add(finalItem);
            }
        }

        double diffAmount = finalTotal - aiTotal;
        Double diffPercentage = aiTotal == 0 ? null : (diffAmount / aiTotal) * 100.0;

        return new QuoteFeedbackDiff(diffAmount, diffPercentage, changed, added, removed);
    }

    private static QuoteLineItem findByName(List<QuoteLineItem> items, String name) {
        String key = normalize(name);
        return items.stream().filter(item -> normalize(item.name()).equals(key)).findFirst().orElse(null);
    }

    private static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}
