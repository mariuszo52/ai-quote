package com.aiquote.backend.lead;

public record LeadAttachmentContent(byte[] bytes, String contentType, String filename) {
}
