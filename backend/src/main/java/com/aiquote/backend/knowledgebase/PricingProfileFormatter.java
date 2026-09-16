package com.aiquote.backend.knowledgebase;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Renders the structured pricing profile into the natural-language text handed to the
 * AI (the onboarding assistant's own context, and the client-facing quote agent's
 * system prompt). Kept separate from the data model so storage stays structured while
 * the AI still "reads" the profile as plain language, same as before Etap 8.
 */
public final class PricingProfileFormatter {

    private PricingProfileFormatter() {
    }

    public static String toPromptText(PricingProfileData data) {
        List<PricingServiceEntry> services = data.services() != null ? data.services() : List.of();
        if (services.isEmpty() && isBlank(data.generalNotes())) {
            return "brak — właściciel nie uzupełnił jeszcze żadnych informacji o cenach.";
        }

        StringBuilder sb = new StringBuilder();
        if (!services.isEmpty()) {
            sb.append("Usługi:\n");
            for (PricingServiceEntry service : services) {
                sb.append("- ").append(formatService(service)).append('\n');
            }
        }
        if (!isBlank(data.generalNotes())) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append("Dodatkowe uwagi: ").append(data.generalNotes().trim());
        }
        return sb.toString().trim();
    }

    private static String formatService(PricingServiceEntry service) {
        List<String> details = new ArrayList<>();

        String modelLabel = pricingModelLabel(service.pricingModel());
        if (modelLabel != null) {
            details.add(modelLabel);
        }
        if (service.basePrice() != null) {
            details.add("cena bazowa: " + formatNumber(service.basePrice()) + unitSuffix(service.unit()));
        }
        if (service.minPrice() != null || service.maxPrice() != null) {
            details.add("widełki: " + formatRange(service.minPrice(), service.maxPrice()) + unitSuffix(service.unit()));
        }
        if (service.minimumCharge() != null) {
            details.add("minimalna opłata: " + formatNumber(service.minimumCharge()));
        }
        if (Boolean.TRUE.equals(service.requiresIndividualQuote())) {
            details.add("wymaga indywidualnej wyceny");
        }
        if (service.factors() != null && !service.factors().isEmpty()) {
            details.add("czynniki wpływające na cenę: " + String.join(", ", service.factors()));
        }
        if (!isBlank(service.notes())) {
            details.add(service.notes().trim());
        }

        return details.isEmpty() ? service.name() : service.name() + " — " + String.join("; ", details);
    }

    private static String pricingModelLabel(String pricingModel) {
        if (pricingModel == null) {
            return null;
        }
        return switch (pricingModel.trim().toUpperCase(Locale.ROOT)) {
            case "HOURLY" -> "stawka godzinowa";
            case "PER_UNIT" -> "cena za jednostkę";
            case "PER_PROJECT" -> "cena za projekt";
            case "INDIVIDUAL" -> "wycena indywidualna";
            default -> null;
        };
    }

    private static String unitSuffix(String unit) {
        return isBlank(unit) ? "" : " / " + unit.trim();
    }

    private static String formatRange(Double min, Double max) {
        if (min != null && max != null) {
            return formatNumber(min) + "–" + formatNumber(max);
        }
        return formatNumber(min != null ? min : max);
    }

    private static String formatNumber(Double value) {
        if (value == null) {
            return "";
        }
        return value == Math.floor(value) ? String.valueOf(value.longValue()) : String.valueOf(value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
