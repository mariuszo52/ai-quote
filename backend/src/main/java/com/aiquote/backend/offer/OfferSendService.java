package com.aiquote.backend.offer;

import com.aiquote.backend.email.EmailAttachment;
import com.aiquote.backend.email.EmailMessage;
import com.aiquote.backend.email.EmailSendException;
import com.aiquote.backend.email.EmailService;
import com.aiquote.backend.file.StorageService;
import com.aiquote.backend.lead.Lead;
import com.aiquote.backend.lead.LeadRepository;
import com.aiquote.backend.quote.Quote;
import com.aiquote.backend.quote.QuoteRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sends the already-generated final offer PDF to the client (Etap 13). Deliberately
 * separate from OfferService (which owns generation/reads) — this is the one place an
 * SMTP failure can happen, and it must never let that failure roll back or lose
 * anything: markSendFailed() below is committed like any other successful write, not
 * thrown as an exception, specifically so the transaction commits instead of rolling
 * back the failure state we just recorded. See sendOffer's javadoc.
 */
@Service
public class OfferSendService {

    private static final String SUBJECT = "Oferta dotycząca Twojego zapytania";

    private final OfferRepository offerRepository;
    private final QuoteRepository quoteRepository;
    private final LeadRepository leadRepository;
    private final StorageService storageService;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;
    private final String frontendUrl;

    public OfferSendService(
            OfferRepository offerRepository,
            QuoteRepository quoteRepository,
            LeadRepository leadRepository,
            StorageService storageService,
            EmailService emailService,
            ObjectMapper objectMapper,
            @Value("${app.frontend-url}") String frontendUrl) {
        this.offerRepository = offerRepository;
        this.quoteRepository = quoteRepository;
        this.leadRepository = leadRepository;
        this.storageService = storageService;
        this.emailService = emailService;
        this.objectMapper = objectMapper;
        this.frontendUrl = frontendUrl;
    }

    /**
     * Always attempts an actual send — including for an already-SENT offer, since the
     * owner may need to resend (e.g. the client reports the email never arrived; SMTP
     * accepting a message is no guarantee of inbox delivery). The frontend's loading
     * state is the only double-click guard; a repeat click here is a repeat send, which
     * is the desired behavior for an explicit "resend" action, not a bug. Only a missing
     * client email blocks the attempt outright (thrown before anything is mutated, so
     * that failure never touches offer state). Any SMTP failure is caught here and
     * recorded on the offer (status=SEND_FAILED, lastSendError) rather than thrown —
     * letting an exception escape a @Transactional method would roll back that exact
     * write, which is the one thing Etap 13 requires must survive a send failure.
     */
    @Transactional
    public OfferResponse sendOffer(Long companyId, Long quoteId) {
        Offer offer = offerRepository.findByQuoteIdAndCompanyId(quoteId, companyId)
                .orElseThrow(() -> new OfferNotFoundException(quoteId));

        if (offer.getClientEmail() == null || offer.getClientEmail().isBlank()) {
            throw new MissingClientEmailException();
        }

        Quote quote = quoteRepository.findByIdAndCompanyId(offer.getQuoteId(), companyId).orElse(null);
        Lead lead = quote != null ? leadRepository.findById(quote.getLeadId()).orElse(null) : null;

        try {
            byte[] pdf = storageService.retrieve(offer.getPdfStorageKey()).readAllBytes();
            String offerLink = frontendUrl + "/offer/" + offer.getPublicToken();
            emailService.send(new EmailMessage(
                    offer.getClientEmail(),
                    SUBJECT,
                    body(offer, offerLink),
                    new EmailAttachment("oferta-" + offer.getId() + ".pdf", "application/pdf", pdf)));
            offer.markSent();
            if (quote != null) {
                quote.markSentToClient();
            }
            if (lead != null) {
                lead.markQuoteSent();
            }
        } catch (EmailSendException | IOException e) {
            offer.markSendFailed(e.getMessage());
            if (quote != null) {
                quote.markSendFailed();
            }
        }

        return toResponse(offer);
    }

    private String body(Offer offer, String offerLink) {
        return """
                Dzień dobry %s,

                Dziękujemy za Twoje zapytanie. W załączniku przesyłamy przygotowaną ofertę \
                (nr %d), na kwotę %s %s.

                Ofertę można również obejrzeć online pod adresem:
                %s

                W razie pytań pozostajemy do dyspozycji.
                """.formatted(offer.getClientName(), offer.getId(), offer.getTotal(), offer.getCurrency(), offerLink);
    }

    private OfferResponse toResponse(Offer offer) {
        return new OfferResponse(
                offer.getId(),
                offer.getQuoteId(),
                offer.getPublicToken(),
                offer.getStatus().name(),
                offer.getCurrency(),
                readItems(offer.getItemsJson()),
                offer.getTotal(),
                offer.getClientName(),
                offer.getClientPhone(),
                offer.getClientEmail(),
                offer.getJobDescription(),
                offer.getEstimatedTimeline(),
                offer.getValidUntil(),
                offer.getSentAt(),
                offer.getLastSendError(),
                offer.getCreatedAt());
    }

    private List<OfferLineItem> readItems(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<OfferLineItem>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse offer items", e);
        }
    }
}
