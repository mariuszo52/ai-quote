package com.aiquote.backend.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * SMTP configured entirely via env vars (SMTP_HOST/PORT/USERNAME/PASSWORD/FROM, see
 * application.yml's spring.mail.* and app.mail.from) — never hardcoded. If SMTP_HOST is
 * unset (e.g. local dev without a mail server configured), send() below simply fails
 * with an EmailSendException, same as any other SMTP failure — callers already have to
 * handle that case (see OfferService#sendOffer).
 */
@Service
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpEmailService(JavaMailSender mailSender, @Value("${app.mail.from}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void send(EmailMessage message) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, message.attachment() != null);
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.body());
            if (!fromAddress.isBlank()) {
                helper.setFrom(fromAddress);
            }
            if (message.attachment() != null) {
                helper.addAttachment(
                        message.attachment().filename(),
                        new ByteArrayResource(message.attachment().content()),
                        message.attachment().contentType());
            }
            mailSender.send(mimeMessage);
        } catch (MessagingException e) {
            throw new EmailSendException("Nie udało się przygotować wiadomości e-mail.", e);
        } catch (MailException e) {
            throw new EmailSendException("Nie udało się wysłać wiadomości e-mail.", e);
        }
    }
}
