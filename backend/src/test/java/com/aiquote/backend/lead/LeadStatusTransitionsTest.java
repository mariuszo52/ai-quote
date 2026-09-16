package com.aiquote.backend.lead;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LeadStatusTransitionsTest {

    @Test
    void allowsTheDocumentedHappyPath() {
        assertThat(LeadStatusTransitions.isManuallyAllowed(LeadStatus.NEW, LeadStatus.CONTACTED)).isTrue();
        assertThat(LeadStatusTransitions.isManuallyAllowed(LeadStatus.CONTACTED, LeadStatus.WON)).isTrue();
        assertThat(LeadStatusTransitions.isManuallyAllowed(LeadStatus.CONTACTED, LeadStatus.LOST)).isTrue();
    }

    @Test
    void allowsLosingOrWinningDirectlyFromNewOrQuoteSent() {
        assertThat(LeadStatusTransitions.isManuallyAllowed(LeadStatus.NEW, LeadStatus.LOST)).isTrue();
        assertThat(LeadStatusTransitions.isManuallyAllowed(LeadStatus.QUOTE_SENT, LeadStatus.WON)).isTrue();
        assertThat(LeadStatusTransitions.isManuallyAllowed(LeadStatus.QUOTE_SENT, LeadStatus.LOST)).isTrue();
    }

    @Test
    void quoteSentIsNeverAManuallyReachableTarget() {
        for (LeadStatus from : LeadStatus.values()) {
            assertThat(LeadStatusTransitions.isManuallyAllowed(from, LeadStatus.QUOTE_SENT))
                    .as("QUOTE_SENT must only be reachable via Lead#markQuoteSent, never PATCH")
                    .isFalse();
        }
    }

    @Test
    void wonAndLostAreTerminal() {
        for (LeadStatus target : LeadStatus.values()) {
            assertThat(LeadStatusTransitions.isManuallyAllowed(LeadStatus.WON, target)).isFalse();
            assertThat(LeadStatusTransitions.isManuallyAllowed(LeadStatus.LOST, target)).isFalse();
        }
    }

    @Test
    void rejectsSkippingBackwardsToNew() {
        assertThat(LeadStatusTransitions.isManuallyAllowed(LeadStatus.CONTACTED, LeadStatus.NEW)).isFalse();
        assertThat(LeadStatusTransitions.isManuallyAllowed(LeadStatus.QUOTE_SENT, LeadStatus.NEW)).isFalse();
    }

    @Test
    void rejectsSelfTransitionsExceptWhereExplicitlyAllowed() {
        assertThat(LeadStatusTransitions.isManuallyAllowed(LeadStatus.NEW, LeadStatus.NEW)).isFalse();
    }
}
