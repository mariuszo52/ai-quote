package com.aiquote.backend.knowledgebase;

import com.aiquote.backend.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "knowledge_sources")
@Getter
@NoArgsConstructor
public class KnowledgeSource extends BaseEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private KnowledgeSourceType type;

    @Column(name = "attachment_id", nullable = false, updatable = false)
    private Long attachmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private KnowledgeSourceStatus status;

    @Column(name = "extracted_summary", columnDefinition = "text")
    private String extractedSummary;

    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    public KnowledgeSource(Long companyId, KnowledgeSourceType type, Long attachmentId) {
        this.companyId = companyId;
        this.type = type;
        this.attachmentId = attachmentId;
        this.status = KnowledgeSourceStatus.UPLOADED;
    }

    public void markProcessing() {
        this.status = KnowledgeSourceStatus.PROCESSING;
    }

    public void markProcessed(String extractedSummary) {
        this.status = KnowledgeSourceStatus.PROCESSED;
        this.extractedSummary = extractedSummary;
    }

    public void markFailed(String reason) {
        this.status = KnowledgeSourceStatus.FAILED;
        this.failureReason = reason;
    }
}
