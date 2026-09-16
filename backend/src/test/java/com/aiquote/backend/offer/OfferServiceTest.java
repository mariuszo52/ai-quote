package com.aiquote.backend.offer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiquote.backend.auth.AppUser;
import com.aiquote.backend.auth.AppUserRepository;
import com.aiquote.backend.company.Company;
import com.aiquote.backend.company.CompanyService;
import com.aiquote.backend.company.CompanyStatus;
import com.aiquote.backend.feedback.QuoteFeedbackService;
import com.aiquote.backend.file.StorageService;
import com.aiquote.backend.lead.Lead;
import com.aiquote.backend.lead.LeadRepository;
import com.aiquote.backend.quote.InvalidQuoteStatusException;
import com.aiquote.backend.quote.Quote;
import com.aiquote.backend.quote.QuoteLineItem;
import com.aiquote.backend.quote.QuoteRepository;
import com.aiquote.backend.quote.QuoteResponse;
import com.aiquote.backend.quote.QuoteService;
import com.aiquote.backend.quote.QuoteStatus;
import com.aiquote.backend.tenant.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Covers Etap 12's core rules: an offer can only be generated from an APPROVED quote,
 * it freezes the approved snapshot (not whatever the quote might say later), and the
 * public/owner response shapes never carry AI-only data (there's nothing on Offer to
 * leak in the first place — see OfferSecurityTest for the structural guarantee).
 */
@ExtendWith(MockitoExtension.class)
class OfferServiceTest {

    @Mock
    private OfferRepository offerRepository;
    @Mock
    private QuoteRepository quoteRepository;
    @Mock
    private LeadRepository leadRepository;
    @Mock
    private QuoteService quoteService;
    @Mock
    private CompanyService companyService;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private StorageService storageService;
    @Mock
    private OfferPdfGenerator offerPdfGenerator;
    @Mock
    private QuoteFeedbackService quoteFeedbackService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OfferService service;

    @BeforeEach
    void setUp() {
        service = new OfferService(
                offerRepository, quoteRepository, leadRepository, quoteService, companyService, appUserRepository, storageService,
                offerPdfGenerator, quoteFeedbackService, objectMapper);
    }

    @Test
    void generatesOfferFromApprovedQuoteSnapshot() {
        Quote quote = approvedQuote(1L, 5L, List.of(item("Malowanie", 10.0, 20.0)));
        Lead lead = lead("Jan Kowalski", "600100200", "jan@example.com");
        Company company = company("Firma Testowa");

        when(quoteRepository.findByIdAndCompanyId(1L, 5L)).thenReturn(Optional.of(quote));
        when(leadRepository.findById(2L)).thenReturn(Optional.of(lead));
        when(companyService.getById(5L)).thenReturn(company);
        when(appUserRepository.findFirstByCompanyIdOrderByIdAsc(5L)).thenReturn(Optional.of(appUser("owner@example.com")));
        when(offerRepository.save(any(Offer.class))).thenAnswer(invocation -> {
            Offer offer = invocation.getArgument(0);
            ReflectionTestUtils.setField(offer, "id", 42L);
            return offer;
        });
        when(offerPdfGenerator.generate(any(), any(), any())).thenReturn(new byte[] {1, 2, 3});
        when(storageService.store(anyLong(), anyString(), anyString(), any(InputStream.class), anyLong())).thenReturn("offers/42.pdf");

        Offer offer = service.generateForApprovedQuote(5L, 1L);

        assertThat(offer.getId()).isEqualTo(42L);
        assertThat(offer.getQuoteId()).isEqualTo(1L);
        assertThat(offer.getTotal()).isEqualTo(200.0);
        assertThat(offer.getClientName()).isEqualTo("Jan Kowalski");
        assertThat(offer.getStatus()).isEqualTo(OfferStatus.READY_TO_SEND);
        assertThat(offer.getPdfStorageKey()).isEqualTo("offers/42.pdf");
        assertThat(offer.getPublicToken()).isNotBlank();
    }

    @Test
    void cannotGenerateOfferForNonApprovedQuote() {
        Quote quote = approvedQuote(1L, 5L, List.of(item("Malowanie", 10.0, 20.0)));
        ReflectionTestUtils.setField(quote, "status", QuoteStatus.WAITING_FOR_OWNER);
        when(quoteRepository.findByIdAndCompanyId(1L, 5L)).thenReturn(Optional.of(quote));

        assertThatThrownBy(() -> service.generateForApprovedQuote(5L, 1L)).isInstanceOf(InvalidQuoteStatusException.class);
    }

    @Test
    void cannotGenerateOfferForDraftQuote() {
        Quote quote = approvedQuote(1L, 5L, List.of(item("Malowanie", 10.0, 20.0)));
        ReflectionTestUtils.setField(quote, "status", QuoteStatus.DRAFT);
        when(quoteRepository.findByIdAndCompanyId(1L, 5L)).thenReturn(Optional.of(quote));

        assertThatThrownBy(() -> service.generateForApprovedQuote(5L, 1L)).isInstanceOf(InvalidQuoteStatusException.class);
    }

    @Test
    void approveQuoteAndGenerateOfferDelegatesToQuoteServiceThenGeneratesOffer() {
        Quote quote = approvedQuote(1L, 5L, List.of(item("Malowanie", 10.0, 20.0)));
        Lead lead = lead("Jan Kowalski", "600100200", "jan@example.com");
        Company company = company("Firma Testowa");
        QuoteResponse quoteResponse = new QuoteResponse(
                1L, 5L, 2L, 3L, "APPROVED", "PLN", List.of(), 200.0, 200.0, 0.8, null, List.of(), List.of(), 200.0, 200.0,
                null, null, null, null, null, List.of(), null, null, null, null, null);

        when(quoteService.approve(5L, 1L)).thenReturn(quoteResponse);
        when(quoteRepository.findByIdAndCompanyId(1L, 5L)).thenReturn(Optional.of(quote));
        when(leadRepository.findById(2L)).thenReturn(Optional.of(lead));
        when(companyService.getById(5L)).thenReturn(company);
        when(appUserRepository.findFirstByCompanyIdOrderByIdAsc(5L)).thenReturn(Optional.empty());
        when(offerRepository.save(any(Offer.class))).thenAnswer(invocation -> {
            Offer offer = invocation.getArgument(0);
            ReflectionTestUtils.setField(offer, "id", 7L);
            return offer;
        });
        when(offerPdfGenerator.generate(any(), any(), any())).thenReturn(new byte[] {1});
        when(storageService.store(anyLong(), anyString(), anyString(), any(InputStream.class), anyLong())).thenReturn("offers/7.pdf");

        QuoteResponse response = service.approveQuoteAndGenerateOffer(5L, 1L);

        assertThat(response.status()).isEqualTo("APPROVED");
        verify(quoteFeedbackService).generateForApprovedQuote(5L, 1L);
    }

    @Test
    void getByQuoteIdIsTenantScoped() {
        when(offerRepository.findByQuoteIdAndCompanyId(1L, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByQuoteId(999L, 1L)).isInstanceOf(OfferNotFoundException.class);
    }

    @Test
    void publicOfferExcludesInternalStatusAndSendMetadata() {
        Offer offer = savedOffer();
        ReflectionTestUtils.setField(offer, "lastSendError", "SMTP down");
        when(offerRepository.findByPublicToken("tok-123")).thenReturn(Optional.of(offer));
        when(companyService.getById(5L)).thenReturn(company("Firma Testowa"));
        when(appUserRepository.findFirstByCompanyIdOrderByIdAsc(5L)).thenReturn(Optional.of(appUser("owner@example.com")));

        PublicOfferResponse response = service.getPublicOffer("tok-123");

        assertThat(response.companyName()).isEqualTo("Firma Testowa");
        assertThat(response.clientName()).isEqualTo("Jan Kowalski");
        assertThat(response.total()).isEqualTo(200.0);
        // PublicOfferResponse has no status/sentAt/lastSendError/quoteId fields at all —
        // the compiler already guarantees they can't leak; this just documents the intent.
    }

    @Test
    void publicOfferThrowsForUnknownToken() {
        when(offerRepository.findByPublicToken("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPublicOffer("missing")).isInstanceOf(OfferNotFoundException.class);
    }

    @Test
    void getPdfReadsFromStorage() throws Exception {
        Offer offer = savedOffer();
        when(offerRepository.findByQuoteIdAndCompanyId(1L, 5L)).thenReturn(Optional.of(offer));
        when(storageService.retrieve("offers/42.pdf")).thenReturn(new ByteArrayInputStream(new byte[] {9, 9, 9}));

        byte[] pdf = service.getPdf(5L, 1L);

        assertThat(pdf).containsExactly(9, 9, 9);
    }

    private Offer savedOffer() {
        List<OfferLineItem> items = List.of(new OfferLineItem("Malowanie", null, 10.0, "m2", 20.0, 200.0));
        Offer offer = new Offer(5L, 1L, "tok-123", "PLN", writeJson(items), 200.0, "Jan Kowalski", "600100200", "jan@example.com", "Opis", null, null, "offers/42.pdf");
        ReflectionTestUtils.setField(offer, "id", 42L);
        return offer;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private QuoteLineItem item(String name, double quantity, double unitPrice) {
        return new QuoteLineItem(name, null, quantity, "m2", unitPrice, quantity * unitPrice, "pricing_profile");
    }

    private Quote approvedQuote(long id, long companyId, List<QuoteLineItem> items) {
        try {
            String itemsJson = objectMapper.writeValueAsString(items);
            double total = items.stream().mapToDouble(QuoteLineItem::totalPrice).sum();
            Quote quote = new Quote(companyId, 3L, 2L, "PLN", itemsJson, total, total, 0.8, "test", "[]");
            ReflectionTestUtils.setField(quote, "id", id);
            quote.approve();
            return quote;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Lead lead(String name, String phone, String email) {
        Lead lead = new Lead(5L, 3L, name, phone, email, "Opis zlecenia klienta.", null, null, "PLN", null);
        ReflectionTestUtils.setField(lead, "id", 2L);
        return lead;
    }

    private Company company(String name) {
        Company company = new Company(name, "firma-testowa", CompanyStatus.ACTIVE);
        ReflectionTestUtils.setField(company, "id", 5L);
        return company;
    }

    private AppUser appUser(String email) {
        return AppUser.local(5L, email, "hash", UserRole.OWNER);
    }
}
