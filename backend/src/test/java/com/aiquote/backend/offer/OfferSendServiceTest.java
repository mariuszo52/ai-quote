package com.aiquote.backend.offer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiquote.backend.email.EmailSendException;
import com.aiquote.backend.email.EmailService;
import com.aiquote.backend.file.StorageService;
import com.aiquote.backend.lead.Lead;
import com.aiquote.backend.lead.LeadRepository;
import com.aiquote.backend.lead.LeadStatus;
import com.aiquote.backend.quote.Quote;
import com.aiquote.backend.quote.QuoteLineItem;
import com.aiquote.backend.quote.QuoteRepository;
import com.aiquote.backend.quote.QuoteStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Covers Etap 13's core rules: sending is blocked without a valid client email, an SMTP
 * failure records the error on the Offer instead of throwing (see OfferSendService's
 * javadoc for why that specific shape matters under @Transactional), and an explicit
 * resend of an already-SENT offer actually re-sends (e.g. the client says it never
 * arrived) rather than being silently swallowed.
 */
@ExtendWith(MockitoExtension.class)
class OfferSendServiceTest {

    @Mock
    private OfferRepository offerRepository;
    @Mock
    private QuoteRepository quoteRepository;
    @Mock
    private LeadRepository leadRepository;
    @Mock
    private StorageService storageService;
    @Mock
    private EmailService emailService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OfferSendService service;

    @BeforeEach
    void setUp() {
        service = new OfferSendService(offerRepository, quoteRepository, leadRepository, storageService, emailService, objectMapper);
    }

    @Test
    void sendsEmailWithPdfAttachmentAndMarksOfferSent() {
        Offer offer = offer("jan@example.com", OfferStatus.READY_TO_SEND);
        Quote quote = approvedQuote();
        Lead lead = lead(LeadStatus.NEW);
        when(offerRepository.findByQuoteIdAndCompanyId(1L, 5L)).thenReturn(Optional.of(offer));
        when(quoteRepository.findByIdAndCompanyId(1L, 5L)).thenReturn(Optional.of(quote));
        when(leadRepository.findById(2L)).thenReturn(Optional.of(lead));
        when(storageService.retrieve("offers/42.pdf")).thenReturn(new ByteArrayInputStream(new byte[] {1, 2, 3}));

        OfferResponse response = service.sendOffer(5L, 1L);

        assertThat(response.status()).isEqualTo("SENT");
        assertThat(offer.getSentAt()).isNotNull();
        assertThat(offer.getLastSendError()).isNull();
        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.SENT_TO_CLIENT);
        assertThat(lead.getStatus()).isEqualTo(LeadStatus.QUOTE_SENT);
        verify(emailService).send(argThat(message ->
                message.to().equals("jan@example.com")
                        && message.subject().equals("Oferta dotycząca Twojego zapytania")
                        && !message.body().contains("http")
                        && message.attachment() != null
                        && message.attachment().filename().equals("oferta-42.pdf")
                        && message.attachment().contentType().equals("application/pdf")
                        && message.attachment().content().length == 3));
    }

    @Test
    void blocksSendingWhenClientHasNoEmail() {
        Offer offer = offer(null, OfferStatus.READY_TO_SEND);
        when(offerRepository.findByQuoteIdAndCompanyId(1L, 5L)).thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> service.sendOffer(5L, 1L)).isInstanceOf(MissingClientEmailException.class);

        assertThat(offer.getStatus()).isEqualTo(OfferStatus.READY_TO_SEND);
        verify(emailService, never()).send(any());
    }

    @Test
    void blocksSendingWhenClientEmailIsBlank() {
        Offer offer = offer("   ", OfferStatus.READY_TO_SEND);
        when(offerRepository.findByQuoteIdAndCompanyId(1L, 5L)).thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> service.sendOffer(5L, 1L)).isInstanceOf(MissingClientEmailException.class);
        verify(emailService, never()).send(any());
    }

    @Test
    void smtpFailureRecordsErrorInsteadOfThrowing() {
        Offer offer = offer("jan@example.com", OfferStatus.READY_TO_SEND);
        Quote quote = approvedQuote();
        when(offerRepository.findByQuoteIdAndCompanyId(1L, 5L)).thenReturn(Optional.of(offer));
        when(quoteRepository.findByIdAndCompanyId(1L, 5L)).thenReturn(Optional.of(quote));
        when(leadRepository.findById(2L)).thenReturn(Optional.of(lead(LeadStatus.NEW)));
        when(storageService.retrieve("offers/42.pdf")).thenReturn(new ByteArrayInputStream(new byte[] {1}));
        org.mockito.Mockito.doThrow(new EmailSendException("SMTP timeout", new RuntimeException()))
                .when(emailService).send(any());

        OfferResponse response = service.sendOffer(5L, 1L);

        assertThat(response.status()).isEqualTo("SEND_FAILED");
        assertThat(offer.getStatus()).isEqualTo(OfferStatus.SEND_FAILED);
        assertThat(offer.getLastSendError()).isEqualTo("SMTP timeout");
        assertThat(offer.getSentAt()).isNull();
        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.SEND_FAILED);
    }

    @Test
    void canRetrySendingAfterAPreviousFailure() {
        Offer offer = offer("jan@example.com", OfferStatus.SEND_FAILED);
        ReflectionTestUtils.setField(offer, "lastSendError", "previous failure");
        Quote quote = approvedQuote();
        ReflectionTestUtils.setField(quote, "status", QuoteStatus.SEND_FAILED);
        when(offerRepository.findByQuoteIdAndCompanyId(1L, 5L)).thenReturn(Optional.of(offer));
        when(quoteRepository.findByIdAndCompanyId(1L, 5L)).thenReturn(Optional.of(quote));
        when(leadRepository.findById(2L)).thenReturn(Optional.of(lead(LeadStatus.NEW)));
        when(storageService.retrieve("offers/42.pdf")).thenReturn(new ByteArrayInputStream(new byte[] {1}));

        OfferResponse response = service.sendOffer(5L, 1L);

        assertThat(response.status()).isEqualTo("SENT");
        assertThat(offer.getLastSendError()).isNull();
        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.SENT_TO_CLIENT);
        verify(emailService, times(1)).send(any());
    }

    @Test
    void quoteSendFailureDoesNotOverrideAWonOrLostLead() {
        Offer offer = offer("jan@example.com", OfferStatus.READY_TO_SEND);
        Quote quote = approvedQuote();
        Lead lead = lead(LeadStatus.WON);
        when(offerRepository.findByQuoteIdAndCompanyId(1L, 5L)).thenReturn(Optional.of(offer));
        when(quoteRepository.findByIdAndCompanyId(1L, 5L)).thenReturn(Optional.of(quote));
        when(leadRepository.findById(2L)).thenReturn(Optional.of(lead));
        when(storageService.retrieve("offers/42.pdf")).thenReturn(new ByteArrayInputStream(new byte[] {1}));

        service.sendOffer(5L, 1L);

        assertThat(lead.getStatus()).isEqualTo(LeadStatus.WON);
    }

    @Test
    void canResendAnAlreadySentOfferWhenTheOwnerExplicitlyRetriggersIt() {
        Offer offer = offer("jan@example.com", OfferStatus.SENT);
        Quote quote = approvedQuote();
        ReflectionTestUtils.setField(quote, "status", QuoteStatus.SENT_TO_CLIENT);
        when(offerRepository.findByQuoteIdAndCompanyId(1L, 5L)).thenReturn(Optional.of(offer));
        when(quoteRepository.findByIdAndCompanyId(1L, 5L)).thenReturn(Optional.of(quote));
        when(leadRepository.findById(2L)).thenReturn(Optional.of(lead(LeadStatus.NEW)));
        when(storageService.retrieve("offers/42.pdf")).thenReturn(new ByteArrayInputStream(new byte[] {1}));

        OfferResponse response = service.sendOffer(5L, 1L);

        assertThat(response.status()).isEqualTo("SENT");
        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.SENT_TO_CLIENT);
        verify(emailService, times(1)).send(any());
    }

    @Test
    void sendIsTenantScoped() {
        when(offerRepository.findByQuoteIdAndCompanyId(1L, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendOffer(999L, 1L)).isInstanceOf(OfferNotFoundException.class);
        verify(emailService, never()).send(any());
    }

    @Test
    void cannotSendWhenNoOfferExistsForTheQuote() {
        // No Offer exists at all for a quote that was never approved (see Etap 12's
        // generateForApprovedQuote guard) — sending 404s exactly like tenant mismatch.
        when(offerRepository.findByQuoteIdAndCompanyId(99L, 5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendOffer(5L, 99L)).isInstanceOf(OfferNotFoundException.class);
    }

    private Quote approvedQuote() {
        List<QuoteLineItem> items = List.of(new QuoteLineItem("Malowanie", null, 10.0, "m2", 20.0, 200.0, "pricing_profile"));
        Quote quote = new Quote(5L, 3L, 2L, "PLN", writeJson(items), 200.0, 200.0, 0.8, "test", "[]");
        ReflectionTestUtils.setField(quote, "id", 1L);
        quote.approve();
        return quote;
    }

    private Lead lead(LeadStatus status) {
        Lead lead = new Lead(5L, 3L, "Jan Kowalski", "600100200", "jan@example.com", "Opis", null, null, "PLN", null);
        ReflectionTestUtils.setField(lead, "id", 2L);
        ReflectionTestUtils.setField(lead, "status", status);
        return lead;
    }

    private Offer offer(String clientEmail, OfferStatus status) {
        List<OfferLineItem> items = List.of(new OfferLineItem("Malowanie", null, 10.0, "m2", 20.0, 200.0));
        Offer offer = new Offer(5L, 1L, "tok-123", "PLN", writeJson(items), 200.0, "Jan Kowalski", "600100200", clientEmail, "Opis", null, null, "offers/42.pdf");
        ReflectionTestUtils.setField(offer, "id", 42L);
        ReflectionTestUtils.setField(offer, "status", status);
        return offer;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
