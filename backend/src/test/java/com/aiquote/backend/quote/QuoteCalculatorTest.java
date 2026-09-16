package com.aiquote.backend.quote;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class QuoteCalculatorTest {

    @Test
    void computesItemTotalFromQuantityTimesUnitPrice() {
        QuoteLineItem item = item("Malowanie ścian", 120.0, 20.0);

        assertThat(QuoteCalculator.itemTotal(item)).isEqualTo(2400.0);
    }

    @Test
    void defaultsQuantityToOneWhenNotProvided() {
        QuoteLineItem item = new QuoteLineItem("Usługa ryczałtowa", null, null, null, 500.0, null, "pricing_profile");

        assertThat(QuoteCalculator.itemTotal(item)).isEqualTo(500.0);
    }

    @Test
    void neverInventsATotalWhenUnitPriceIsUnknown() {
        QuoteLineItem item = new QuoteLineItem("Nietypowa naprawa", null, 1.0, null, null, null, null);

        assertThat(QuoteCalculator.itemTotal(item)).isNull();
    }

    @Test
    void withComputedTotalOverwritesWhateverTotalPriceWasPassedIn() {
        QuoteLineItem item = new QuoteLineItem("Usługa", null, 2.0, null, 10.0, 999.0, null);

        QuoteLineItem result = QuoteCalculator.withComputedTotal(item);

        assertThat(result.totalPrice()).isEqualTo(20.0);
    }

    @Test
    void subtotalSumsOnlyItemsWithKnownPrices() {
        List<QuoteLineItem> items = List.of(
                item("Malowanie ścian", 120.0, 20.0), // 2400
                new QuoteLineItem("Nietypowa naprawa", null, 1.0, null, null, null, null), // unknown, contributes 0
                item("Gruntowanie", 120.0, 5.0)); // 600

        assertThat(QuoteCalculator.subtotal(items)).isEqualTo(3000.0);
    }

    @Test
    void subtotalOfEmptyListIsZero() {
        assertThat(QuoteCalculator.subtotal(List.of())).isZero();
    }

    @Test
    void subtotalOfAllUnknownPricesIsZeroNotGuessed() {
        List<QuoteLineItem> items = List.of(
                new QuoteLineItem("A", null, 1.0, null, null, null, null),
                new QuoteLineItem("B", null, 1.0, null, null, null, null));

        assertThat(QuoteCalculator.subtotal(items)).isZero();
    }

    private static QuoteLineItem item(String name, double quantity, double unitPrice) {
        return new QuoteLineItem(name, null, quantity, "m2", unitPrice, null, "pricing_profile");
    }
}
