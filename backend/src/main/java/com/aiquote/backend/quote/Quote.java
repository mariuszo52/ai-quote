package com.aiquote.backend.quote;

import com.aiquote.backend.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * An AI-drafted, itemized quote for a lead's conversation. Deliberately separate from
 * Lead's own estimatedPriceMin/Max/currency fields — those come from the live client
 * chat's quick propose_quote range (shown to the client before they hand over contact
 * details); this is the richer, line-itemized internal draft the owner reviews
 * afterward. Lead reaches it via leadId, no data duplicated between the two.
 */
@Entity
@Table(name = "quotes")
@Getter
@NoArgsConstructor
public class Quote extends BaseEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "conversation_id", nullable = false, updatable = false)
    private Long conversationId;

    @Column(name = "lead_id", nullable = false, updatable = false)
    private Long leadId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private QuoteStatus status;

    @Column(nullable = false, length = 8)
    private String currency;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "items_json", nullable = false, columnDefinition = "jsonb")
    private String itemsJson;

    @Column(nullable = false)
    private double subtotal;

    @Column(nullable = false)
    private double total;

    @Column(name = "ai_confidence")
    private Double aiConfidence;

    @Column(name = "ai_reasoning", columnDefinition = "text")
    private String aiReasoning;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "uncertain_factors_json", nullable = false, columnDefinition = "jsonb")
    private String uncertainFactorsJson;

    /** Frozen the moment the AI creates the quote — never touched again, so an owner
     * edit can always be compared back to "what AI originally said" (Etap 10 #2/#3). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_items_json", nullable = false, columnDefinition = "jsonb")
    private String aiItemsJson;

    @Column(name = "ai_subtotal", nullable = false)
    private double aiSubtotal;

    @Column(name = "ai_total", nullable = false)
    private double aiTotal;

    /** Shown to the client once a future stage sends the final offer — never exposed
     * anywhere public today. */
    @Column(name = "client_note", columnDefinition = "text")
    private String clientNote;

    /** Owner-only, never shown to the client, ever. */
    @Column(name = "internal_note", columnDefinition = "text")
    private String internalNote;

    /** Both optional and owner-set (Etap 12) — frozen into the Offer at approve() time,
     * same as everything else on this entity. */
    @Column(name = "estimated_timeline")
    private String estimatedTimeline;

    @Column(name = "offer_valid_until")
    private LocalDate offerValidUntil;

    /** Snapshot frozen at approve() time — the exact version the owner signed off on,
     * independent of whatever "current" ends up meaning later (e.g. once PDF generation
     * or further statuses exist). Null until approved. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "approved_items_json", columnDefinition = "jsonb")
    private String approvedItemsJson;

    @Column(name = "approved_subtotal")
    private Double approvedSubtotal;

    @Column(name = "approved_total")
    private Double approvedTotal;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Quote(
            Long companyId,
            Long conversationId,
            Long leadId,
            String currency,
            String itemsJson,
            double subtotal,
            double total,
            Double aiConfidence,
            String aiReasoning,
            String uncertainFactorsJson) {
        this.companyId = companyId;
        this.conversationId = conversationId;
        this.leadId = leadId;
        this.status = QuoteStatus.WAITING_FOR_OWNER;
        this.currency = currency;
        this.itemsJson = itemsJson;
        this.subtotal = subtotal;
        this.total = total;
        this.aiConfidence = aiConfidence;
        this.aiReasoning = aiReasoning;
        this.uncertainFactorsJson = uncertainFactorsJson;
        this.aiItemsJson = itemsJson;
        this.aiSubtotal = subtotal;
        this.aiTotal = total;
        this.updatedAt = Instant.now();
    }

    /**
     * Caller (QuoteService) must already have verified status != APPROVED. First edit
     * moves WAITING_FOR_OWNER -> OWNER_EDITED; further edits just stay OWNER_EDITED.
     */
    public void applyOwnerEdit(
            String itemsJson,
            double subtotal,
            double total,
            String clientNote,
            String internalNote,
            String estimatedTimeline,
            LocalDate offerValidUntil) {
        this.itemsJson = itemsJson;
        this.subtotal = subtotal;
        this.total = total;
        this.clientNote = clientNote;
        this.internalNote = internalNote;
        this.estimatedTimeline = estimatedTimeline;
        this.offerValidUntil = offerValidUntil;
        if (this.status == QuoteStatus.WAITING_FOR_OWNER) {
            this.status = QuoteStatus.OWNER_EDITED;
        }
        this.updatedAt = Instant.now();
    }

    /** Caller must already have verified status is WAITING_FOR_OWNER or OWNER_EDITED. */
    public void approve() {
        this.status = QuoteStatus.APPROVED;
        this.approvedItemsJson = this.itemsJson;
        this.approvedSubtotal = this.subtotal;
        this.approvedTotal = this.total;
        this.approvedAt = Instant.now();
        this.updatedAt = this.approvedAt;
    }

    /** Called by offer.OfferSendService once the offer email actually sends. Reachable
     * from APPROVED, a previous SEND_FAILED (retry), or SENT_TO_CLIENT itself — the
     * owner can explicitly resend an already-sent offer (e.g. the client says it never
     * arrived), which re-enters this same terminal status rather than being blocked. */
    public void markSentToClient() {
        if (status != QuoteStatus.APPROVED && status != QuoteStatus.SEND_FAILED && status != QuoteStatus.SENT_TO_CLIENT) {
            throw new InvalidQuoteStatusException("Tylko zaakceptowana wycena może zostać oznaczona jako wysłana.");
        }
        this.status = QuoteStatus.SENT_TO_CLIENT;
        this.updatedAt = Instant.now();
    }

    /** Called by offer.OfferSendService when a send attempt fails — the quote stays
     * retryable (see markSentToClient's SEND_FAILED case) whether this was the first
     * attempt or a manual resend of an already-sent offer. */
    public void markSendFailed() {
        if (status != QuoteStatus.APPROVED && status != QuoteStatus.SEND_FAILED && status != QuoteStatus.SENT_TO_CLIENT) {
            throw new InvalidQuoteStatusException("Tylko zaakceptowana wycena może zostać oznaczona jako nieudana wysyłka.");
        }
        this.status = QuoteStatus.SEND_FAILED;
        this.updatedAt = Instant.now();
    }
}
