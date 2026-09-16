package com.aiquote.backend.email;

/** attachment is nullable — most transactional emails this app sends won't need one. */
public record EmailMessage(String to, String subject, String body, EmailAttachment attachment) {
}
