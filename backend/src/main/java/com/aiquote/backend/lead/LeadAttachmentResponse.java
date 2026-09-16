package com.aiquote.backend.lead;

import com.aiquote.backend.conversation.Attachment;

public record LeadAttachmentResponse(Long id, String filename, String contentType) {

    public static LeadAttachmentResponse from(Attachment attachment) {
        return new LeadAttachmentResponse(attachment.getId(), attachment.getOriginalFilename(), attachment.getMimeType());
    }
}
