package com.aiquote.backend.feedback;

import com.aiquote.backend.quote.InvalidQuoteStatusException;
import com.aiquote.backend.quote.Quote;
import com.aiquote.backend.quote.QuoteLineItem;
import com.aiquote.backend.quote.QuoteNotFoundException;
import com.aiquote.backend.quote.QuoteRepository;
import com.aiquote.backend.quote.QuoteStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the AI-vs-final comparison data layer (Etap 15) — depends on quote (never the
 * reverse), same direction as offer. Called from
 * offer.OfferService#approveQuoteAndGenerateOffer right after approval, in the same
 * transaction, so a Quote can never end up APPROVED without a matching QuoteFeedback
 * row.
 */
@Service
@RequiredArgsConstructor
public class QuoteFeedbackService {

    private final QuoteFeedbackRepository feedbackRepository;
    private final QuoteRepository quoteRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public QuoteFeedback generateForApprovedQuote(Long companyId, Long quoteId) {
        if (feedbackRepository.existsByQuoteId(quoteId)) {
            return feedbackRepository.findByQuoteIdAndCompanyId(quoteId, companyId).orElseThrow();
        }

        Quote quote = quoteRepository.findByIdAndCompanyId(quoteId, companyId)
                .orElseThrow(() -> new QuoteNotFoundException(quoteId));
        if (quote.getStatus() != QuoteStatus.APPROVED) {
            throw new InvalidQuoteStatusException("Dane porównawcze można wygenerować tylko dla zaakceptowanej wyceny.");
        }

        List<QuoteLineItem> aiItems = readItems(quote.getAiItemsJson());
        List<QuoteLineItem> finalItems = readItems(quote.getApprovedItemsJson());
        QuoteFeedbackDiff diff = QuoteFeedbackDiffBuilder.build(aiItems, finalItems, quote.getAiTotal(), quote.getApprovedTotal());

        QuoteFeedback feedback = new QuoteFeedback(
                companyId,
                quoteId,
                quote.getAiTotal(),
                quote.getApprovedTotal(),
                diff.diffAmount(),
                diff.diffPercentage(),
                quote.getAiItemsJson(),
                quote.getApprovedItemsJson(),
                writeJson(diff.changedItems()),
                writeJson(diff.addedItems()),
                writeJson(diff.removedItems()));
        return feedbackRepository.save(feedback);
    }

    public QuoteFeedbackResponse getByQuoteId(Long companyId, Long quoteId) {
        return toResponse(requireOwned(companyId, quoteId));
    }

    @Transactional
    public QuoteFeedbackResponse submitFeedback(Long companyId, Long quoteId, SubmitFeedbackRequest request) {
        QuoteFeedback feedback = requireOwned(companyId, quoteId);
        feedback.submitFeedback(request.reason(), blankToNull(request.note()));
        return toResponse(feedback);
    }

    private QuoteFeedback requireOwned(Long companyId, Long quoteId) {
        return feedbackRepository.findByQuoteIdAndCompanyId(quoteId, companyId)
                .orElseThrow(() -> new QuoteFeedbackNotFoundException(quoteId));
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private QuoteFeedbackResponse toResponse(QuoteFeedback feedback) {
        return new QuoteFeedbackResponse(
                feedback.getId(),
                feedback.getQuoteId(),
                feedback.getAiTotal(),
                feedback.getFinalTotal(),
                feedback.getDiffAmount(),
                feedback.getDiffPercentage(),
                readItems(feedback.getAiItemsJson()),
                readItems(feedback.getFinalItemsJson()),
                readChangedItems(feedback.getChangedItemsJson()),
                readItems(feedback.getAddedItemsJson()),
                readItems(feedback.getRemovedItemsJson()),
                feedback.getReason() != null ? feedback.getReason().name() : null,
                feedback.getNote(),
                feedback.getReasonSubmittedAt(),
                feedback.getCreatedAt());
    }

    private List<QuoteLineItem> readItems(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<QuoteLineItem>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse quote feedback items", e);
        }
    }

    private List<ChangedItem> readChangedItems(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<ChangedItem>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse quote feedback changed items", e);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize quote feedback data", e);
        }
    }
}
