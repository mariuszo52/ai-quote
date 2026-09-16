package com.aiquote.backend.conversation;

import com.aiquote.backend.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Generic stored-file record shared by knowledge-base uploads (Etap 3) and client-chat
 * photo uploads (Etap 5). messageId starts null (photo uploaded ahead of the caption)
 * and is filled in once the client's next text message is sent — see
 * QuoteAgentService.sendMessage.
 */
@Entity
@Table(name = "attachments")
@Getter
@NoArgsConstructor
public class Attachment extends BaseEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "conversation_id", updatable = false)
    private Long conversationId;

    @Column(name = "message_id")
    private Long messageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private AttachmentKind kind;

    @Column(name = "storage_key", nullable = false, updatable = false)
    private String storageKey;

    @Column(name = "original_filename", updatable = false)
    private String originalFilename;

    @Column(name = "mime_type", updatable = false)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    public Attachment(
            Long companyId,
            AttachmentKind kind,
            String storageKey,
            String originalFilename,
            String mimeType,
            long sizeBytes) {
        this.companyId = companyId;
        this.kind = kind;
        this.storageKey = storageKey;
        this.originalFilename = originalFilename;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
    }

    public Attachment(
            Long companyId,
            Long conversationId,
            AttachmentKind kind,
            String storageKey,
            String originalFilename,
            String mimeType,
            long sizeBytes) {
        this(companyId, kind, storageKey, originalFilename, mimeType, sizeBytes);
        this.conversationId = conversationId;
    }

    public void attachToMessage(Long messageId) {
        this.messageId = messageId;
    }
}
