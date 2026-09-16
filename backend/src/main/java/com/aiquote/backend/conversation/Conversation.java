package com.aiquote.backend.conversation;

import com.aiquote.backend.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "conversations")
@Getter
@NoArgsConstructor
public class Conversation extends BaseEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private ConversationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ConversationStatus status;

    @Column(name = "public_token", unique = true, updatable = false)
    private String publicToken;

    @Column(name = "last_quote_json", columnDefinition = "text")
    private String lastQuoteJson;

    public Conversation(Long companyId, ConversationType type) {
        this.companyId = companyId;
        this.type = type;
        this.status = ConversationStatus.ACTIVE;
        this.publicToken = type == ConversationType.CLIENT_QUOTE ? UUID.randomUUID().toString().replace("-", "") : null;
    }

    public void complete() {
        this.status = ConversationStatus.COMPLETED;
    }

    public void setLastQuote(String lastQuoteJson) {
        this.lastQuoteJson = lastQuoteJson;
    }
}
