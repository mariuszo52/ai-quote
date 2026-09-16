package com.aiquote.backend.quote;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class QuoteChangeSummaryBuilderTest {

    @Test
    void noChangesProducesEmptySummary() {
        List<QuoteLineItem> items = List.of(item("Malowanie", 2400.0));

        String summary = QuoteChangeSummaryBuilder.build(items, items, "PLN");

        assertThat(summary).isEmpty();
    }

    @Test
    void priceChangeIsReported() {
        List<QuoteLineItem> before = List.of(item("Malowanie", 2400.0));
        List<QuoteLineItem> after = List.of(item("Malowanie", 2800.0));

        String summary = QuoteChangeSummaryBuilder.build(before, after, "PLN");

        assertThat(summary).isEqualTo("Malowanie: 2400.0 PLN → 2800.0 PLN");
    }

    @Test
    void removedItemIsReported() {
        List<QuoteLineItem> before = List.of(item("Malowanie", 2400.0), item("Gruntowanie", 600.0));
        List<QuoteLineItem> after = List.of(item("Malowanie", 2400.0));

        String summary = QuoteChangeSummaryBuilder.build(before, after, "PLN");

        assertThat(summary).isEqualTo("Gruntowanie: usunięto");
    }

    @Test
    void addedItemIsReported() {
        List<QuoteLineItem> before = List.of(item("Malowanie", 2400.0));
        List<QuoteLineItem> after = List.of(item("Malowanie", 2400.0), item("Gruntowanie", 600.0));

        String summary = QuoteChangeSummaryBuilder.build(before, after, "PLN");

        assertThat(summary).isEqualTo("Nowa pozycja: Gruntowanie — 600.0 PLN");
    }

    @Test
    void multipleChangesAreJoined() {
        List<QuoteLineItem> before = List.of(item("Malowanie", 2400.0), item("Gruntowanie", 600.0));
        List<QuoteLineItem> after = List.of(item("Malowanie", 2800.0), item("Sprzątanie", 200.0));

        String summary = QuoteChangeSummaryBuilder.build(before, after, "PLN");

        assertThat(summary)
                .contains("Malowanie: 2400.0 PLN → 2800.0 PLN")
                .contains("Gruntowanie: usunięto")
                .contains("Nowa pozycja: Sprzątanie — 200.0 PLN");
    }

    @Test
    void matchesItemNamesTrimmedAndCaseInsensitively() {
        List<QuoteLineItem> before = List.of(item("Malowanie ścian", 2400.0));
        List<QuoteLineItem> after = List.of(item("  malowanie ŚCIAN  ", 2500.0));

        String summary = QuoteChangeSummaryBuilder.build(before, after, "PLN");

        assertThat(summary).isEqualTo("Malowanie ścian: 2400.0 PLN → 2500.0 PLN");
    }

    private static QuoteLineItem item(String name, Double totalPrice) {
        return new QuoteLineItem(name, null, 1.0, null, totalPrice, totalPrice, null);
    }
}
