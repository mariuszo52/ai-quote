package com.aiquote.backend.knowledgebase;

import java.util.ArrayList;
import java.util.List;

/** Renders the "Moje materiały" catalog into the natural-language text handed to the
 * client-facing quote agent's system prompt — same idea as PricingProfileFormatter, kept
 * as a separate class since materials (concrete parts/devices) and services (priced
 * labor) are different concepts with different fields. */
public final class PriceListFormatter {

    private PriceListFormatter() {
    }

    public static String toPromptText(List<PriceListItem> items) {
        if (items.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("Materiały i urządzenia:\n");
        for (PriceListItem item : items) {
            sb.append("- ").append(formatItem(item)).append('\n');
        }
        return sb.toString().trim();
    }

    private static String formatItem(PriceListItem item) {
        List<String> details = new ArrayList<>();
        if (!isBlank(item.getCategory())) {
            details.add(item.getCategory().trim());
        }
        if (item.getPrice() != null) {
            details.add(formatNumber(item.getPrice()) + " zł" + unitSuffix(item.getUnit()));
        } else if (!isBlank(item.getUnit())) {
            details.add("jednostka: " + item.getUnit().trim());
        }
        return details.isEmpty() ? item.getName() : item.getName() + " — " + String.join("; ", details);
    }

    private static String unitSuffix(String unit) {
        return isBlank(unit) ? "" : " / " + unit.trim();
    }

    private static String formatNumber(Double value) {
        return value == Math.floor(value) ? String.valueOf(value.longValue()) : String.valueOf(value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
