package com.aiquote.backend.quote;

import com.aiquote.backend.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One entry per owner edit that actually changed something — deliberately not a full
 * versioning system (no stored before/after JSON blobs), just a human-readable summary
 * line. Enough for "what changed and when" without the complexity of a real revision
 * history; see QuoteChangeSummaryBuilder for how the summary text is produced.
 */
@Entity
@Table(name = "quote_change_log")
@Getter
@NoArgsConstructor
public class QuoteChangeLogEntry extends BaseEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "quote_id", nullable = false, updatable = false)
    private Long quoteId;

    @Column(nullable = false, columnDefinition = "text", updatable = false)
    private String summary;

    public QuoteChangeLogEntry(Long companyId, Long quoteId, String summary) {
        this.companyId = companyId;
        this.quoteId = quoteId;
        this.summary = summary;
    }
}
