package com.aiquote.backend.email;

public record EmailAttachment(String filename, String contentType, byte[] content) {
}
