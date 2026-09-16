package com.aiquote.backend.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Covers the SENT_TO_CLIENT/SEND_FAILED transition guards added in Etap 14 —
 * markSentToClient/markSendFailed are only called from offer.OfferSendService, but the
 * guard itself belongs to Quote (same pattern as approve()).
 */
class QuoteTest {

    @Test
    void markSentToClientMovesFromApproved() {
        Quote quote = quote();
        quote.approve();

        quote.markSentToClient();

        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.SENT_TO_CLIENT);
    }

    @Test
    void markSentToClientCanRetryFromSendFailed() {
        Quote quote = quote();
        quote.approve();
        quote.markSendFailed();

        quote.markSentToClient();

        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.SENT_TO_CLIENT);
    }

    @Test
    void markSentToClientRejectsNonApprovedQuote() {
        Quote quote = quote();

        assertThatThrownBy(quote::markSentToClient).isInstanceOf(InvalidQuoteStatusException.class);
    }

    @Test
    void markSendFailedRejectsNonApprovedQuote() {
        Quote quote = quote();

        assertThatThrownBy(quote::markSendFailed).isInstanceOf(InvalidQuoteStatusException.class);
    }

    @Test
    void markSentToClientAllowsResendingAnAlreadySentQuote() {
        Quote quote = quote();
        quote.approve();
        quote.markSentToClient();

        quote.markSentToClient();

        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.SENT_TO_CLIENT);
    }

    @Test
    void markSendFailedAllowsAResendAttemptOnAnAlreadySentQuoteToFail() {
        Quote quote = quote();
        quote.approve();
        quote.markSentToClient();

        quote.markSendFailed();

        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.SEND_FAILED);
    }

    private Quote quote() {
        List<QuoteLineItem> items = List.of(new QuoteLineItem("Malowanie", null, 10.0, "m2", 20.0, 200.0, "pricing_profile"));
        String itemsJson = writeJson(items);
        Quote quote = new Quote(5L, 3L, 2L, "PLN", itemsJson, 200.0, 200.0, 0.8, "test", "[]");
        ReflectionTestUtils.setField(quote, "id", 1L);
        return quote;
    }

    private String writeJson(Object value) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
