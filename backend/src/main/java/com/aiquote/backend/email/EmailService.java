package com.aiquote.backend.email;

/**
 * Outbound email abstraction. SmtpEmailService is the only implementation — callers
 * never touch JavaMailSender directly, same reasoning as StorageService/MinioStorageService.
 */
public interface EmailService {

    /** @throws EmailSendException if the message could not be sent (SMTP unreachable, auth failure, etc). */
    void send(EmailMessage message);
}
