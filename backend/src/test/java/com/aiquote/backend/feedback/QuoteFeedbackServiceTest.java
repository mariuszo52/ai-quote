package com.aiquote.backend.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiquote.backend.quote.InvalidQuoteStatusException;
import com.aiquote.backend.quote.Quote;
import com.aiquote.backend.quote.QuoteLineItem;
import com.aiquote.backend.quote.QuoteNotFoundException;
import com.aiquote.backend.quote.QuoteRepository;
import com.aiquote.backend.quote.QuoteStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Covers Etap 15's data-layer rules: a feedback row is only generated for an APPROVED
 * quote, it's never regenerated once it exists (idempotent), submitting a reason/note
 * is fully optional and tenant-scoped, and nothing here ever blocks approval itself
 * (submitFeedback is a separate call entirely).
 */
@ExtendWith(MockitoExtension.class)
class QuoteFeedbackServiceTest {

    @Mock
    private QuoteFeedbackRepository feedbackRepository;
    @Mock
    private QuoteRepository quoteRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private QuoteFeedbackService service;

    @BeforeEach
    void setUp() {
        service = new QuoteFeedbackService(feedbackRepository, quoteRepository, objectMapper);
    }

    @Test
    void generatesFeedbackWithNoChangesWhenOwnerAcceptedAiAsIs() {
        List<QuoteLineItem> items = List.of(item("Malowanie", 10.0, 20.0));
        Quote quote = approvedQuote(items, items, 200.0, 200.0);
        when(quoteRepository.findByIdAndCompanyId(1L, 5L)).thenReturn(Optional.of(quote));
        when(feedbackRepository.save(any(QuoteFeedback.class))).thenAnswer(inv -> inv.getArgument(0));

        QuoteFeedback feedback = service.generateForApprovedQuote(5L, 1L);

        assertThat(feedback.getAiTotal()).isEqualTo(200.0);
        assertThat(feedback.getFinalTotal()).isEqualTo(200.0);
        assertThat(feedback.getDiffAmount()).isEqualTo(0.0);
        assertThat(feedback.getChangedItemsJson()).isEqualTo("[]");
    }

    @Test
    void generatesFeedbackWithAPriceChange() {
        List<QuoteLineItem> aiItems = List.of(item("Malowanie", 10.0, 20.0));
        List<QuoteLineItem> finalItems = List.of(item("Malowanie", 10.0, 25.0));
        Quote quote = approvedQuote(aiItems, finalItems, 200.0, 250.0);
        when(quoteRepository.findByIdAndCompanyId(1L, 5L)).thenReturn(Optional.of(quote));
        when(feedbackRepository.save(any(QuoteFeedback.class))).thenAnswer(inv -> inv.getArgument(0));

        QuoteFeedback feedback = service.generateForApprovedQuote(5L, 1L);

        assertThat(feedback.getDiffAmount()).isEqualTo(50.0);
        assertThat(feedback.getDiffPercentage()).isEqualTo(25.0);
        assertThat(feedback.getChangedItemsJson()).contains("Malowanie");
    }

    @Test
    void cannotGenerateForNonApprovedQuote() {
        Quote quote = approvedQuote(List.of(), List.of(), 0.0, 0.0);
        ReflectionTestUtils.setField(quote, "status", QuoteStatus.WAITING_FOR_OWNER);
        when(quoteRepository.findByIdAndCompanyId(1L, 5L)).thenReturn(Optional.of(quote));

        assertThatThrownBy(() -> service.generateForApprovedQuote(5L, 1L)).isInstanceOf(InvalidQuoteStatusException.class);
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    void generateIsTenantScoped() {
        when(quoteRepository.findByIdAndCompanyId(1L, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateForApprovedQuote(999L, 1L)).isInstanceOf(QuoteNotFoundException.class);
    }

    @Test
    void generatingTwiceForTheSameQuoteDoesNotCreateADuplicateRow() {
        QuoteFeedback existing = feedback();
        when(feedbackRepository.existsByQuoteId(1L)).thenReturn(true);
        when(feedbackRepository.findByQuoteIdAndCompanyId(1L, 5L)).thenReturn(Optional.of(existing));

        QuoteFeedback result = service.generateForApprovedQuote(5L, 1L);

        assertThat(result).isSameAs(existing);
        verify(feedbackRepository, never()).save(any());
        verify(quoteRepository, never()).findByIdAndCompanyId(any(), any());
    }

    @Test
    void submitFeedbackRecordsOptionalReasonAndNote() {
        QuoteFeedback feedback = feedback();
        when(feedbackRepository.findByQuoteIdAndCompanyId(1L, 5L)).thenReturn(Optional.of(feedback));

        QuoteFeedbackResponse response = service.submitFeedback(5L, 1L, new SubmitFeedbackRequest(FeedbackReason.PRICE_TOO_LOW, "Materiały droższe niż zwykle."));

        assertThat(response.reason()).isEqualTo("PRICE_TOO_LOW");
        assertThat(response.note()).isEqualTo("Materiały droższe niż zwykle.");
        assertThat(response.reasonSubmittedAt()).isNotNull();
    }

    @Test
    void submitFeedbackAcceptsAnEmptySubmissionWithoutError() {
        QuoteFeedback feedback = feedback();
        when(feedbackRepository.findByQuoteIdAndCompanyId(1L, 5L)).thenReturn(Optional.of(feedback));

        QuoteFeedbackResponse response = service.submitFeedback(5L, 1L, new SubmitFeedbackRequest(null, null));

        assertThat(response.reason()).isNull();
        assertThat(response.note()).isNull();
    }

    @Test
    void submitFeedbackIsTenantScoped() {
        when(feedbackRepository.findByQuoteIdAndCompanyId(1L, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submitFeedback(999L, 1L, new SubmitFeedbackRequest(FeedbackReason.OTHER, null)))
                .isInstanceOf(QuoteFeedbackNotFoundException.class);
    }

    @Test
    void getByQuoteIdIsTenantScoped() {
        when(feedbackRepository.findByQuoteIdAndCompanyId(1L, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByQuoteId(999L, 1L)).isInstanceOf(QuoteFeedbackNotFoundException.class);
    }

    private QuoteLineItem item(String name, double quantity, double unitPrice) {
        return new QuoteLineItem(name, null, quantity, "m2", unitPrice, quantity * unitPrice, "pricing_profile");
    }

    private Quote approvedQuote(List<QuoteLineItem> aiItems, List<QuoteLineItem> finalItems, double aiTotal, double finalTotal) {
        try {
            String aiJson = objectMapper.writeValueAsString(aiItems);
            String finalJson = objectMapper.writeValueAsString(finalItems);
            Quote quote = new Quote(5L, 3L, 2L, "PLN", aiJson, aiTotal, aiTotal, 0.8, "test", "[]");
            ReflectionTestUtils.setField(quote, "id", 1L);
            quote.approve();
            ReflectionTestUtils.setField(quote, "approvedItemsJson", finalJson);
            ReflectionTestUtils.setField(quote, "approvedTotal", finalTotal);
            return quote;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private QuoteFeedback feedback() {
        QuoteFeedback feedback = new QuoteFeedback(5L, 1L, 200.0, 250.0, 50.0, 25.0, "[]", "[]", "[]", "[]", "[]");
        ReflectionTestUtils.setField(feedback, "id", 9L);
        return feedback;
    }
}
