package com.aiquote.backend.lead;

import com.aiquote.backend.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "leads")
@Getter
@NoArgsConstructor
public class Lead extends BaseEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "conversation_id", nullable = false, updatable = false)
    private Long conversationId;

    @Column(name = "client_name", nullable = false, updatable = false)
    private String clientName;

    @Column(name = "client_phone", nullable = false, updatable = false)
    private String clientPhone;

    @Column(name = "client_email", updatable = false)
    private String clientEmail;

    @Column(name = "ai_summary", columnDefinition = "text", updatable = false)
    private String aiSummary;

    @Column(name = "estimated_price_min", updatable = false)
    private Double estimatedPriceMin;

    @Column(name = "estimated_price_max", updatable = false)
    private Double estimatedPriceMax;

    @Column(updatable = false, length = 8)
    private String currency;

    @Column(name = "uncertain_notes", columnDefinition = "text", updatable = false)
    private String uncertainNotes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LeadStatus status;

    public Lead(
            Long companyId,
            Long conversationId,
            String clientName,
            String clientPhone,
            String clientEmail,
            String aiSummary,
            Double estimatedPriceMin,
            Double estimatedPriceMax,
            String currency,
            String uncertainNotes) {
        this.companyId = companyId;
        this.conversationId = conversationId;
        this.clientName = clientName;
        this.clientPhone = clientPhone;
        this.clientEmail = clientEmail;
        this.aiSummary = aiSummary;
        this.estimatedPriceMin = estimatedPriceMin;
        this.estimatedPriceMax = estimatedPriceMax;
        this.currency = currency;
        this.uncertainNotes = uncertainNotes;
        this.status = LeadStatus.NEW;
    }

    /** Manual owner-driven change — caller (LeadService) has already validated the
     * transition against LeadStatusTransitions. */
    public void updateStatus(LeadStatus status) {
        this.status = status;
    }

    /** System-driven: called once the final offer email actually sends (see
     * offer.OfferSendService). Deliberately looser than the manual transition table —
     * it can fire from NEW or CONTACTED — but never overrides a closed deal. */
    public void markQuoteSent() {
        if (status == LeadStatus.WON || status == LeadStatus.LOST) {
            return;
        }
        this.status = LeadStatus.QUOTE_SENT;
    }
}
