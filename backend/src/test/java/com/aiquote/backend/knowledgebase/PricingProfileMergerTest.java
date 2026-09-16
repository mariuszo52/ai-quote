package com.aiquote.backend.knowledgebase;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Covers the core guarantee behind Etap 8: an AI-driven update must never silently
 * erase pricing knowledge the owner (or a previous AI turn) already established, just
 * because the latest turn didn't happen to restate it.
 */
class PricingProfileMergerTest {

    @Test
    void mergingIntoEmptyProfileAcceptsIncomingWholesale() {
        PricingProfileData existing = PricingProfileData.empty();
        PricingProfileData incoming = new PricingProfileData(
                List.of(service("Instalacja elektryczna", "HOURLY", 120.0)), "Dojazd do 20km gratis");

        PricingProfileData merged = PricingProfileMerger.merge(existing, incoming);

        assertThat(merged.services()).hasSize(1);
        assertThat(merged.services().get(0).name()).isEqualTo("Instalacja elektryczna");
        assertThat(merged.services().get(0).basePrice()).isEqualTo(120.0);
        assertThat(merged.generalNotes()).isEqualTo("Dojazd do 20km gratis");
    }

    @Test
    void fieldsOmittedByIncomingUpdateAreNotErased() {
        PricingServiceEntry existingEntry = new PricingServiceEntry(
                "Naprawa hydrauliczna", "HOURLY", "godzina", 100.0, 80.0, 150.0, 100.0,
                List.of("odległość", "trudność dostępu"), false, "Wycena w dni robocze.");
        PricingProfileData existing = new PricingProfileData(List.of(existingEntry), "Płatność gotówką lub przelewem.");

        // AI only learned the base price changed this turn — everything else is omitted (null).
        PricingServiceEntry incomingEntry = new PricingServiceEntry(
                "Naprawa hydrauliczna", null, null, 130.0, null, null, null, null, null, null);
        PricingProfileData incoming = new PricingProfileData(List.of(incomingEntry), null);

        PricingProfileData merged = PricingProfileMerger.merge(existing, incoming);

        assertThat(merged.services()).hasSize(1);
        PricingServiceEntry mergedEntry = merged.services().get(0);
        assertThat(mergedEntry.basePrice()).isEqualTo(130.0); // updated
        assertThat(mergedEntry.pricingModel()).isEqualTo("HOURLY"); // preserved
        assertThat(mergedEntry.unit()).isEqualTo("godzina"); // preserved
        assertThat(mergedEntry.minPrice()).isEqualTo(80.0); // preserved
        assertThat(mergedEntry.maxPrice()).isEqualTo(150.0); // preserved
        assertThat(mergedEntry.minimumCharge()).isEqualTo(100.0); // preserved
        assertThat(mergedEntry.factors()).containsExactly("odległość", "trudność dostępu"); // preserved
        assertThat(mergedEntry.requiresIndividualQuote()).isFalse(); // preserved
        assertThat(mergedEntry.notes()).isEqualTo("Wycena w dni robocze."); // preserved
        assertThat(merged.generalNotes()).isEqualTo("Płatność gotówką lub przelewem."); // preserved
    }

    @Test
    void explicitNonNullIncomingValueOverridesExisting() {
        PricingProfileData existing = new PricingProfileData(
                List.of(service("Montaż oświetlenia", "PER_UNIT", 50.0)), null);
        PricingProfileData incoming = new PricingProfileData(
                List.of(service("Montaż oświetlenia", "PER_UNIT", 65.0)), null);

        PricingProfileData merged = PricingProfileMerger.merge(existing, incoming);

        assertThat(merged.services().get(0).basePrice()).isEqualTo(65.0);
    }

    @Test
    void newServiceIsAppendedWithoutTouchingExistingOnes() {
        PricingProfileData existing = new PricingProfileData(List.of(service("Usługa A", "HOURLY", 100.0)), null);
        PricingProfileData incoming = new PricingProfileData(List.of(service("Usługa B", "PER_PROJECT", 500.0)), null);

        PricingProfileData merged = PricingProfileMerger.merge(existing, incoming);

        assertThat(merged.services()).extracting(PricingServiceEntry::name).containsExactly("Usługa A", "Usługa B");
        assertThat(merged.services().get(0).basePrice()).isEqualTo(100.0);
        assertThat(merged.services().get(1).basePrice()).isEqualTo(500.0);
    }

    @Test
    void serviceNamesAreMatchedTrimmedAndCaseInsensitively() {
        PricingProfileData existing = new PricingProfileData(List.of(service("Sprzątanie biur", "HOURLY", 40.0)), null);
        PricingProfileData incoming = new PricingProfileData(List.of(service("  sprzątanie BIUR  ", null, 45.0)), null);

        PricingProfileData merged = PricingProfileMerger.merge(existing, incoming);

        assertThat(merged.services()).hasSize(1);
        assertThat(merged.services().get(0).name()).isEqualTo("Sprzątanie biur"); // existing casing kept
        assertThat(merged.services().get(0).basePrice()).isEqualTo(45.0);
    }

    @Test
    void blankGeneralNotesFromIncomingPreserveExisting() {
        PricingProfileData existing = new PricingProfileData(List.of(), "Ważna istniejąca notatka.");
        PricingProfileData incoming = new PricingProfileData(List.of(), "   ");

        PricingProfileData merged = PricingProfileMerger.merge(existing, incoming);

        assertThat(merged.generalNotes()).isEqualTo("Ważna istniejąca notatka.");
    }

    @Test
    void nonBlankGeneralNotesFromIncomingReplaceExisting() {
        PricingProfileData existing = new PricingProfileData(List.of(), "Stara notatka.");
        PricingProfileData incoming = new PricingProfileData(List.of(), "Nowa notatka.");

        PricingProfileData merged = PricingProfileMerger.merge(existing, incoming);

        assertThat(merged.generalNotes()).isEqualTo("Nowa notatka.");
    }

    @Test
    void nullFactorsPreserveExistingButExplicitEmptyListReplaces() {
        PricingServiceEntry existingEntry = new PricingServiceEntry(
                "Usługa", "HOURLY", null, 100.0, null, null, null, List.of("pilność"), null, null);
        PricingProfileData existing = new PricingProfileData(List.of(existingEntry), null);

        PricingServiceEntry incomingWithNullFactors = new PricingServiceEntry(
                "Usługa", null, null, null, null, null, null, null, null, null);
        PricingProfileData mergedPreserved = PricingProfileMerger.merge(existing, new PricingProfileData(List.of(incomingWithNullFactors), null));
        assertThat(mergedPreserved.services().get(0).factors()).containsExactly("pilność");

        PricingServiceEntry incomingWithEmptyFactors = new PricingServiceEntry(
                "Usługa", null, null, null, null, null, null, List.of(), null, null);
        PricingProfileData mergedCleared = PricingProfileMerger.merge(existing, new PricingProfileData(List.of(incomingWithEmptyFactors), null));
        assertThat(mergedCleared.services().get(0).factors()).isEmpty();
    }

    @Test
    void incomingServiceWithBlankNameIsIgnored() {
        PricingProfileData existing = new PricingProfileData(List.of(service("Usługa A", "HOURLY", 100.0)), null);
        PricingServiceEntry blankNamed = new PricingServiceEntry(
                "   ", "HOURLY", null, 999.0, null, null, null, null, null, null);
        PricingProfileData incoming = new PricingProfileData(List.of(blankNamed), null);

        PricingProfileData merged = PricingProfileMerger.merge(existing, incoming);

        assertThat(merged.services()).hasSize(1);
        assertThat(merged.services().get(0).basePrice()).isEqualTo(100.0);
    }

    private static PricingServiceEntry service(String name, String pricingModel, Double basePrice) {
        return new PricingServiceEntry(name, pricingModel, null, basePrice, null, null, null, null, null, null);
    }
}
