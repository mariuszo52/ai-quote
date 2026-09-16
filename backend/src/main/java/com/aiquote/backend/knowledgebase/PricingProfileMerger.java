package com.aiquote.backend.knowledgebase;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Merges an AI-proposed pricing update into the existing profile without ever silently
 * dropping information the AI didn't happen to restate this turn.
 *
 * The rule, applied uniformly to every field: a null (or blank, for free-text fields)
 * value on the incoming side means "not addressed this turn — keep whatever is already
 * there." A non-null/non-blank incoming value means the conversation actually provided
 * that value, so it wins. Services are matched by name (trimmed, case-insensitive);
 * unmatched incoming services are appended as new entries, and existing services the AI
 * didn't mention at all are left completely untouched.
 *
 * This is what stands between "AI forgets to restate something" and that something
 * quietly vanishing from the owner's pricing knowledge — manual edits go through a
 * separate, deliberate full-replace path (CompanyPricingProfileService.replaceManually)
 * since the owner reviewing a form and hitting save is not a "did the AI mean to change
 * this" situation.
 */
public final class PricingProfileMerger {

    private PricingProfileMerger() {
    }

    public static PricingProfileData merge(PricingProfileData existing, PricingProfileData incoming) {
        List<PricingServiceEntry> existingServices = existing.services() != null ? existing.services() : List.of();
        List<PricingServiceEntry> incomingServices = (incoming.services() != null ? incoming.services() : List.<PricingServiceEntry>of())
                .stream()
                .filter(entry -> !isBlank(entry.name()))
                .toList();

        List<PricingServiceEntry> merged = new ArrayList<>();
        Set<String> handledKeys = new HashSet<>();

        for (PricingServiceEntry existingEntry : existingServices) {
            String key = normalizeName(existingEntry.name());
            PricingServiceEntry incomingMatch = incomingServices.stream()
                    .filter(entry -> normalizeName(entry.name()).equals(key))
                    .findFirst()
                    .orElse(null);
            merged.add(incomingMatch != null ? mergeEntry(existingEntry, incomingMatch) : existingEntry);
            handledKeys.add(key);
        }

        for (PricingServiceEntry incomingEntry : incomingServices) {
            String key = normalizeName(incomingEntry.name());
            if (handledKeys.add(key)) {
                merged.add(incomingEntry);
            }
        }

        String mergedNotes = isBlank(incoming.generalNotes()) ? existing.generalNotes() : incoming.generalNotes();

        return new PricingProfileData(merged, mergedNotes);
    }

    private static PricingServiceEntry mergeEntry(PricingServiceEntry existing, PricingServiceEntry incoming) {
        return new PricingServiceEntry(
                existing.name(),
                firstNonBlank(incoming.pricingModel(), existing.pricingModel()),
                firstNonBlank(incoming.unit(), existing.unit()),
                firstNonNull(incoming.basePrice(), existing.basePrice()),
                firstNonNull(incoming.minPrice(), existing.minPrice()),
                firstNonNull(incoming.maxPrice(), existing.maxPrice()),
                firstNonNull(incoming.minimumCharge(), existing.minimumCharge()),
                incoming.factors() != null ? incoming.factors() : existing.factors(),
                firstNonNull(incoming.requiresIndividualQuote(), existing.requiresIndividualQuote()),
                firstNonBlank(incoming.notes(), existing.notes()));
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String firstNonBlank(String incoming, String existing) {
        return isBlank(incoming) ? existing : incoming;
    }

    private static <T> T firstNonNull(T incoming, T existing) {
        return incoming != null ? incoming : existing;
    }
}
