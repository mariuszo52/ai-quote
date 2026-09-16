package com.aiquote.backend.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiquote.backend.quote.QuoteLineItem;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuoteFeedbackDiffBuilderTest {

    @Test
    void noChangesProducesEmptyDiffLists() {
        List<QuoteLineItem> items = List.of(item("Malowanie", 10.0, 20.0));

        QuoteFeedbackDiff diff = QuoteFeedbackDiffBuilder.build(items, items, 200.0, 200.0);

        assertThat(diff.changedItems()).isEmpty();
        assertThat(diff.addedItems()).isEmpty();
        assertThat(diff.removedItems()).isEmpty();
        assertThat(diff.diffAmount()).isEqualTo(0.0);
        assertThat(diff.diffPercentage()).isEqualTo(0.0);
    }

    @Test
    void detectsAPriceChangeOnAMatchedItem() {
        List<QuoteLineItem> ai = List.of(item("Malowanie", 10.0, 20.0));
        List<QuoteLineItem> finalItems = List.of(item("Malowanie", 10.0, 25.0));

        QuoteFeedbackDiff diff = QuoteFeedbackDiffBuilder.build(ai, finalItems, 200.0, 250.0);

        assertThat(diff.changedItems()).hasSize(1);
        ChangedItem changed = diff.changedItems().get(0);
        assertThat(changed.name()).isEqualTo("Malowanie");
        assertThat(changed.aiUnitPrice()).isEqualTo(20.0);
        assertThat(changed.finalUnitPrice()).isEqualTo(25.0);
        assertThat(diff.addedItems()).isEmpty();
        assertThat(diff.removedItems()).isEmpty();
        assertThat(diff.diffAmount()).isEqualTo(50.0);
        assertThat(diff.diffPercentage()).isEqualTo(25.0);
    }

    @Test
    void detectsAnAddedItem() {
        List<QuoteLineItem> ai = List.of(item("Malowanie", 10.0, 20.0));
        List<QuoteLineItem> finalItems = List.of(item("Malowanie", 10.0, 20.0), item("Gruntowanie", 10.0, 5.0));

        QuoteFeedbackDiff diff = QuoteFeedbackDiffBuilder.build(ai, finalItems, 200.0, 250.0);

        assertThat(diff.addedItems()).extracting(QuoteLineItem::name).containsExactly("Gruntowanie");
        assertThat(diff.changedItems()).isEmpty();
        assertThat(diff.removedItems()).isEmpty();
    }

    @Test
    void detectsARemovedItem() {
        List<QuoteLineItem> ai = List.of(item("Malowanie", 10.0, 20.0), item("Gruntowanie", 10.0, 5.0));
        List<QuoteLineItem> finalItems = List.of(item("Malowanie", 10.0, 20.0));

        QuoteFeedbackDiff diff = QuoteFeedbackDiffBuilder.build(ai, finalItems, 250.0, 200.0);

        assertThat(diff.removedItems()).extracting(QuoteLineItem::name).containsExactly("Gruntowanie");
        assertThat(diff.changedItems()).isEmpty();
        assertThat(diff.addedItems()).isEmpty();
    }

    @Test
    void matchesItemsByNormalizedNameIgnoringCaseAndWhitespace() {
        List<QuoteLineItem> ai = List.of(item("  Malowanie  ", 10.0, 20.0));
        List<QuoteLineItem> finalItems = List.of(item("MALOWANIE", 10.0, 20.0));

        QuoteFeedbackDiff diff = QuoteFeedbackDiffBuilder.build(ai, finalItems, 200.0, 200.0);

        assertThat(diff.changedItems()).isEmpty();
        assertThat(diff.addedItems()).isEmpty();
        assertThat(diff.removedItems()).isEmpty();
    }

    @Test
    void diffPercentageIsNullWhenAiTotalIsZero() {
        QuoteFeedbackDiff diff = QuoteFeedbackDiffBuilder.build(List.of(), List.of(item("Malowanie", 1.0, 100.0)), 0.0, 100.0);

        assertThat(diff.diffPercentage()).isNull();
        assertThat(diff.diffAmount()).isEqualTo(100.0);
    }

    private QuoteLineItem item(String name, double quantity, double unitPrice) {
        return new QuoteLineItem(name, null, quantity, "m2", unitPrice, quantity * unitPrice, "pricing_profile");
    }
}
