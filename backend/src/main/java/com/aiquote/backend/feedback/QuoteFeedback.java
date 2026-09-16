package com.aiquote.backend.feedback;

import com.aiquote.backend.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One row per approved Quote — the permanent AI-vs-final comparison (Etap 15). Created
 * once, atomically with approval (see offer.OfferService#approveQuoteAndGenerateOffer),
 * and never overwritten: aiItemsJson/finalItemsJson/the diff numbers are frozen at
 * creation exactly like Quote's own ai-/approved- snapshot fields, so "what AI said"
 * can always be compared against "what the owner actually charged," regardless of how
 * many further Quote/Offer changes happen later. Only reason/note/reasonSubmittedAt are
 * ever updated — the owner can attach that after the fact (see submitFeedback).
 *
 * Deliberately just a data layer: nothing here feeds a model or changes pricing
 * automatically. It exists so a future stage can query it — see this class's own
 * javadoc history for that scope boundary.
 */
@Entity
@Table(name = "quote_feedback")
@Getter
@NoArgsConstructor
public class QuoteFeedback extends BaseEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "quote_id", nullable = false, updatable = false)
    private Long quoteId;

    @Column(name = "ai_total", nullable = false, updatable = false)
    private double aiTotal;

    @Column(name = "final_total", nullable = false, updatable = false)
    private double finalTotal;

    @Column(name = "diff_amount", nullable = false, updatable = false)
    private double diffAmount;

    @Column(name = "diff_percentage", updatable = false)
    private Double diffPercentage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_items_json", nullable = false, columnDefinition = "jsonb", updatable = false)
    private String aiItemsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "final_items_json", nullable = false, columnDefinition = "jsonb", updatable = false)
    private String finalItemsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "changed_items_json", nullable = false, columnDefinition = "jsonb", updatable = false)
    private String changedItemsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "added_items_json", nullable = false, columnDefinition = "jsonb", updatable = false)
    private String addedItemsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "removed_items_json", nullable = false, columnDefinition = "jsonb", updatable = false)
    private String removedItemsJson;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private FeedbackReason reason;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "reason_submitted_at")
    private Instant reasonSubmittedAt;

    public QuoteFeedback(
            Long companyId,
            Long quoteId,
            double aiTotal,
            double finalTotal,
            double diffAmount,
            Double diffPercentage,
            String aiItemsJson,
            String finalItemsJson,
            String changedItemsJson,
            String addedItemsJson,
            String removedItemsJson) {
        this.companyId = companyId;
        this.quoteId = quoteId;
        this.aiTotal = aiTotal;
        this.finalTotal = finalTotal;
        this.diffAmount = diffAmount;
        this.diffPercentage = diffPercentage;
        this.aiItemsJson = aiItemsJson;
        this.finalItemsJson = finalItemsJson;
        this.changedItemsJson = changedItemsJson;
        this.addedItemsJson = addedItemsJson;
        this.removedItemsJson = removedItemsJson;
    }

    /** Optional, owner-supplied, never required — see QuoteFeedbackService#submitFeedback. */
    public void submitFeedback(FeedbackReason reason, String note) {
        this.reason = reason;
        this.note = note;
        this.reasonSubmittedAt = Instant.now();
    }
}
