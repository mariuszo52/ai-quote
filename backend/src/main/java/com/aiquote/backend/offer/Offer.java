package com.aiquote.backend.offer;

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
 * The client-facing final offer — created exactly once, at the moment a Quote is
 * approved (see OfferService#approveQuoteAndGenerateOffer), from a frozen snapshot of
 * that Quote's approved items/total/client data. Nothing here is ever recomputed from a
 * later Quote edit: the Quote is locked (APPROVED) by the time this exists, and even if
 * that constraint ever changed, this row must stay exactly what the client was shown.
 *
 * Deliberately excludes every AI-only field (confidence, reasoning, uncertainFactors,
 * changeLog, internalNote) — OfferPdfGenerator and PublicOfferController only ever see
 * this type, never Quote, so there is no code path for that data to leak to a client.
 */
@Entity
@Table(name = "offers")
@Getter
@NoArgsConstructor
public class Offer extends BaseEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "quote_id", nullable = false, updatable = false)
    private Long quoteId;

    @Column(name = "public_token", nullable = false, updatable = false, length = 64)
    private String publicToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OfferStatus status;

    @Column(nullable = false, length = 8, updatable = false)
    private String currency;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "items_json", nullable = false, columnDefinition = "jsonb", updatable = false)
    private String itemsJson;

    @Column(nullable = false, updatable = false)
    private double total;

    @Column(name = "client_name", nullable = false, updatable = false)
    private String clientName;

    @Column(name = "client_phone", nullable = false, updatable = false)
    private String clientPhone;

    @Column(name = "client_email", updatable = false)
    private String clientEmail;

    @Column(name = "job_description", columnDefinition = "text", updatable = false)
    private String jobDescription;

    @Column(name = "estimated_timeline", updatable = false)
    private String estimatedTimeline;

    @Column(name = "valid_until", updatable = false)
    private LocalDate validUntil;

    /** Not updatable=false: set in a second step, once the row has an id to name the
     * PDF after (see OfferService#generateForApprovedQuote — the PDF itself renders
     * "Oferta nr {id}", so the offer must be inserted before the PDF can be built). */
    @Column(name = "pdf_storage_key", nullable = false)
    private String pdfStorageKey;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "last_send_error", columnDefinition = "text")
    private String lastSendError;

    public Offer(
            Long companyId,
            Long quoteId,
            String publicToken,
            String currency,
            String itemsJson,
            double total,
            String clientName,
            String clientPhone,
            String clientEmail,
            String jobDescription,
            String estimatedTimeline,
            LocalDate validUntil,
            String pdfStorageKey) {
        this.companyId = companyId;
        this.quoteId = quoteId;
        this.publicToken = publicToken;
        this.status = OfferStatus.READY_TO_SEND;
        this.currency = currency;
        this.itemsJson = itemsJson;
        this.total = total;
        this.clientName = clientName;
        this.clientPhone = clientPhone;
        this.clientEmail = clientEmail;
        this.jobDescription = jobDescription;
        this.estimatedTimeline = estimatedTimeline;
        this.validUntil = validUntil;
        this.pdfStorageKey = pdfStorageKey;
    }

    public void attachPdf(String pdfStorageKey) {
        this.pdfStorageKey = pdfStorageKey;
    }

    public void markSent() {
        this.status = OfferStatus.SENT;
        this.sentAt = Instant.now();
        this.lastSendError = null;
    }

    public void markSendFailed(String error) {
        this.status = OfferStatus.SEND_FAILED;
        this.lastSendError = error;
    }
}
