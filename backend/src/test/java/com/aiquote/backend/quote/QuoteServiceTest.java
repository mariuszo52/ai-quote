package com.aiquote.backend.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiquote.backend.company.Company;
import com.aiquote.backend.company.CompanyService;
import com.aiquote.backend.lead.Lead;
import com.aiquote.backend.lead.LeadNotFoundException;
import com.aiquote.backend.lead.LeadRepository;
import com.aiquote.backend.pdf.QuotePdfGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Covers both Etap 9 (tenant-scoped reads) and Etap 10 (owner edit / approval)
 * behavior. QuoteRepository's lookups are scoped by companyId at the query level
 * (findByIdAndCompanyId, findByLeadIdAndCompanyId) — a lookup under the wrong tenant
 * simply finds nothing, so every mutating operation is exercised both for its happy
 * path and for "wrong company" to confirm it 404s instead of leaking or mutating
 * another company's quote.
 */
@ExtendWith(MockitoExtension.class)
class QuoteServiceTest {

    @Mock
    private QuoteRepository quoteRepository;
    @Mock
    private LeadRepository leadRepository;
    @Mock
    private QuoteChangeLogRepository changeLogRepository;
    @Mock
    private CompanyService companyService;
    @Mock
    private QuotePdfGenerator quotePdfGenerator;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private QuoteService service;

    @BeforeEach
    void setUp() {
        service = new QuoteService(quoteRepository, leadRepository, changeLogRepository, companyService, quotePdfGenerator, objectMapper);
    }

    @Test
    void getDetailReturnsQuoteWhenItBelongsToTheRequestingCompany() {
        Quote quote = quote(10L, 5L, List.of(item("Malowanie", 1.0, 2400.0)));
        when(quoteRepository.findByIdAndCompanyId(10L, 5L)).thenReturn(Optional.of(quote));

        QuoteResponse response = service.getDetail(5L, 10L);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.companyId()).isEqualTo(5L);
    }

    @Test
    void getDetailThrowsWhenQuoteBelongsToAnotherCompany() {
        when(quoteRepository.findByIdAndCompanyId(10L, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetail(999L, 10L)).isInstanceOf(QuoteNotFoundException.class);
    }

    @Test
    void getByLeadIdIsAlsoTenantScoped() {
        when(quoteRepository.findByLeadIdAndCompanyId(7L, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByLeadId(999L, 7L)).isInstanceOf(QuoteNotFoundException.class);
    }

    @Test
    void updateRecomputesTotalsFromEditedUnitPrice() {
        Quote quote = quote(1L, 5L, List.of(item("Malowanie", 10.0, 20.0))); // 200
        when(quoteRepository.findByIdAndCompanyId(1L, 5L)).thenReturn(Optional.of(quote));

        QuoteResponse response = service.update(5L, 1L, request(item("Malowanie", 10.0, 25.0)));

        assertThat(response.total()).isEqualTo(250.0);
        assertThat(response.subtotal()).isEqualTo(250.0);
        assertThat(response.items().get(0).totalPrice()).isEqualTo(250.0);
    }

    @Test
    void updateIgnoresClientSuppliedTotalPriceAndRecomputesFromServer() {
        Quote quote = quote(1L, 5L, List.of(item("Malowanie", 10.0, 20.0)));
        when(quoteRepository.findByIdAndCompanyId(1L, 5L)).thenReturn(Optional.of(quote));

        // Frontend sends a fabricated totalPrice of 999999 alongside the real quantity/unitPrice.
        QuoteLineItem tampered = new QuoteLineItem("Malowanie", null, 10.0, "m2", 20.0, 999999.0, null);
        QuoteResponse response = service.update(5L, 1L, new UpdateQuoteRequest(List.of(tampered), null, null, null, null));

        assertThat(response.items().get(0).totalPrice()).isEqualTo(200.0);
        assertThat(response.total()).isEqualTo(200.0);
    }

    @Test
    void updateCanAddANewItem() {
        Quote quote = quote(1L, 5L, List.of(item("Malowanie", 10.0, 20.0))); // 200
        when(quoteRepository.findByIdAndCompanyId(1L, 5L)).thenReturn(Optional.of(quote));

        QuoteResponse response = service.update(5L, 1L, request(item("Malowanie", 10.0, 20.0), item("Gruntowanie", 10.0, 5.0)));

        assertThat(response.items()).hasSize(2);
        assertThat(response.total()).isEqualTo(250.0); // 200 + 50
    }

    @Test
    void updateCanRemoveAnItem() {
        Quote quote = quote(1L, 5L, List.of(item("Malowanie", 10.0, 20.0), item("Gruntowanie", 10.0, 5.0))); // 250
        when(quoteRepository.findByIdAndCompanyId(1L, 5L)).thenReturn(Optional.of(quote));

        QuoteResponse response = service.update(5L, 1L, request(item("Malowanie", 10.0, 20.0)));

        assertThat(response.items()).hasSize(1);
        assertThat(response.total()).isEqualTo(200.0);
    }

    @Test
    void updateTransitionsWaitingForOwnerToOwnerEdited() {
        Quote quote = quote(1L, 5L, List.of(item("Malowanie", 10.0, 20.0)));
        when(quoteRepository.findByIdAndCompanyId(1L, 5L)).thenReturn(Optional.of(quote));

        QuoteResponse response = service.update(5L, 1L, request(item("Malowanie", 10.0, 25.0)));

        assertThat(response.status()).isEqualTo("OWNER_EDITED");
    }

    @Test
    void updateOnAlreadyOwnerEditedQuoteStaysOwnerEdited() {
        Quote quote = quote(1L, 5L, List.of(item("Malowanie", 10.0, 20.0)));
        ReflectionTestUtils.setField(quote, "status", QuoteStatus.OWNER_EDITED);
        when(quoteRepository.findByIdAndCompanyId(1L, 5L)).thenReturn(Optional.of(quote));

        QuoteResponse response = service.update(5L, 1L, request(item("Malowanie", 10.0, 30.0)));

        assertThat(response.status()).isEqualTo("OWNER_EDITED");
    }

    @Test
    void updateSavesClientAndInternalNotes() {
        Quote quote = quote(1L, 5L, List.of(item("Malowanie", 10.0, 20.0)));
        when(quoteRepository.findByIdAndCompanyId(1L, 5L)).thenReturn(Optional.of(quote));

        QuoteResponse response = service.update(
                5L, 1L, new UpdateQuoteRequest(List.of(item("Malowanie", 10.0, 20.0)), "Dziękujemy za zapytanie!", "Klient trudny, dopilnować terminu.", null, null));

        assertThat(response.clientNote()).isEqualTo("Dziękujemy za zapytanie!");
        assertThat(response.internalNote()).isEqualTo("Klient trudny, dopilnować terminu.");
    }

    @Test
    void updateWritesChangeLogEntryDescribingThePriceChange() {
        Quote quote = quote(1L, 5L, List.of(item("Malowanie", 10.0, 20.0))); // 200
        when(quoteRepository.findByIdAndCompanyId(1L, 5L)).thenReturn(Optional.of(quote));

        service.update(5L, 1L, request(item("Malowanie", 10.0, 28.0))); // 280

        verify(changeLogRepository).save(argThat(entry ->
                entry.getQuoteId().equals(1L) && entry.getCompanyId().equals(5L) && entry.getSummary().contains("200.0")
                        && entry.getSummary().contains("280.0")));
    }

    @Test
    void updateWithNoActualChangeDoesNotWriteChangeLog() {
        Quote quote = quote(1L, 5L, List.of(item("Malowanie", 10.0, 20.0)));
        when(quoteRepository.findByIdAndCompanyId(1L, 5L)).thenReturn(Optional.of(quote));

        service.update(5L, 1L, request(item("Malowanie", 10.0, 20.0)));

        verify(changeLogRepository, never()).save(any());
    }

    @Test
    void cannotEditAnApprovedQuote() {
        Quote quote = quote(1L, 5L, List.of(item("Malowanie", 10.0, 20.0)));
        ReflectionTestUtils.setField(quote, "status", QuoteStatus.APPROVED);
        ReflectionTestUtils.setField(quote, "approvedAt", java.time.Instant.now());
        when(quoteRepository.findByIdAndCompanyId(1L, 5L)).thenReturn(Optional.of(quote));

        assertThatThrownBy(() -> service.update(5L, 1L, request(item("Malowanie", 10.0, 30.0))))
                .isInstanceOf(InvalidQuoteStatusException.class);
    }

    /** Etap 20 regression test: SEND_FAILED and SENT_TO_CLIENT are only reachable after
     * approve(), so they must be just as frozen as APPROVED itself — otherwise an owner
     * edit here silently diverges from the already-generated Offer snapshot, since a
     * resend uses that frozen snapshot, not this quote's live fields. */
    @Test
    void cannotEditAQuoteThatWasApprovedEvenIfItsStatusMovedPastApproved() {
        Quote quote = quote(1L, 5L, List.of(item("Malowanie", 10.0, 20.0)));
        ReflectionTestUtils.setField(quote, "status", QuoteStatus.SEND_FAILED);
        ReflectionTestUtils.setField(quote, "approvedAt", java.time.Instant.now());
        when(quoteRepository.findByIdAndCompanyId(1L, 5L)).thenReturn(Optional.of(quote));

        assertThatThrownBy(() -> service.update(5L, 1L, request(item("Malowanie", 10.0, 30.0))))
                .isInstanceOf(InvalidQuoteStatusException.class);
    }

    @Test
    void updateThrowsWhenQuoteBelongsToAnotherCompany() {
        when(quoteRepository.findByIdAndCompanyId(1L, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(999L, 1L, request(item("Malowanie", 10.0, 20.0))))
                .isInstanceOf(QuoteNotFoundException.class);
    }

    @Test
    void approveTransitionsWaitingForOwnerToApprovedAndFreezesSnapshot() {
        Quote quote = quote(1L, 5L, List.of(item("Malowanie", 10.0, 20.0)));
        when(quoteRepository.findByIdAndCompanyId(1L, 5L)).thenReturn(Optional.of(quote));

        QuoteResponse response = service.approve(5L, 1L);

        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(quote.getApprovedAt()).isNotNull();
        assertThat(quote.getApprovedTotal()).isEqualTo(200.0);
        assertThat(quote.getApprovedItemsJson()).isEqualTo(quote.getItemsJson());
    }

    @Test
    void approveFromOwnerEditedAlsoWorks() {
        Quote quote = quote(1L, 5L, List.of(item("Malowanie", 10.0, 20.0)));
        ReflectionTestUtils.setField(quote, "status", QuoteStatus.OWNER_EDITED);
        when(quoteRepository.findByIdAndCompanyId(1L, 5L)).thenReturn(Optional.of(quote));

        QuoteResponse response = service.approve(5L, 1L);

        assertThat(response.status()).isEqualTo("APPROVED");
    }

    @Test
    void cannotApproveAnAlreadyApprovedQuote() {
        Quote quote = quote(1L, 5L, List.of(item("Malowanie", 10.0, 20.0)));
        ReflectionTestUtils.setField(quote, "status", QuoteStatus.APPROVED);
        when(quoteRepository.findByIdAndCompanyId(1L, 5L)).thenReturn(Optional.of(quote));

        assertThatThrownBy(() -> service.approve(5L, 1L)).isInstanceOf(InvalidQuoteStatusException.class);
    }

    @Test
    void cannotApproveACancelledQuote() {
        Quote quote = quote(1L, 5L, List.of(item("Malowanie", 10.0, 20.0)));
        ReflectionTestUtils.setField(quote, "status", QuoteStatus.CANCELLED);
        when(quoteRepository.findByIdAndCompanyId(1L, 5L)).thenReturn(Optional.of(quote));

        assertThatThrownBy(() -> service.approve(5L, 1L)).isInstanceOf(InvalidQuoteStatusException.class);
    }

    @Test
    void approveThrowsWhenQuoteBelongsToAnotherCompany() {
        when(quoteRepository.findByIdAndCompanyId(1L, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(999L, 1L)).isInstanceOf(QuoteNotFoundException.class);
    }

    @Test
    void getPdfLoadsQuoteLeadAndCompanyAndDelegatesToGenerator() {
        Quote quote = quote(1L, 5L, List.of(item("Malowanie", 10.0, 20.0)));
        ReflectionTestUtils.setField(quote, "leadId", 2L);
        Lead lead = new Lead(5L, 3L, "Jan Kowalski", "123456789", null, null, null, null, "PLN", null);
        Company company = new Company("Firma Testowa", "firma-testowa", com.aiquote.backend.company.CompanyStatus.ACTIVE);
        byte[] pdfBytes = {1, 2, 3};

        when(quoteRepository.findByIdAndCompanyId(1L, 5L)).thenReturn(Optional.of(quote));
        when(leadRepository.findById(2L)).thenReturn(Optional.of(lead));
        when(companyService.getById(5L)).thenReturn(company);
        when(quotePdfGenerator.generate(quote, lead, company)).thenReturn(pdfBytes);

        byte[] result = service.getPdf(5L, 1L);

        assertThat(result).isEqualTo(pdfBytes);
    }

    @Test
    void getPdfThrowsWhenQuoteBelongsToAnotherCompany() {
        when(quoteRepository.findByIdAndCompanyId(1L, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPdf(999L, 1L)).isInstanceOf(QuoteNotFoundException.class);
    }

    @Test
    void getPdfThrowsWhenLeadIsMissing() {
        Quote quote = quote(1L, 5L, List.of(item("Malowanie", 10.0, 20.0)));
        ReflectionTestUtils.setField(quote, "leadId", 2L);
        when(quoteRepository.findByIdAndCompanyId(1L, 5L)).thenReturn(Optional.of(quote));
        when(leadRepository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPdf(5L, 1L)).isInstanceOf(LeadNotFoundException.class);
    }

    private UpdateQuoteRequest request(QuoteLineItem... items) {
        return new UpdateQuoteRequest(List.of(items), null, null, null, null);
    }

    private QuoteLineItem item(String name, double quantity, double unitPrice) {
        return new QuoteLineItem(name, null, quantity, "m2", unitPrice, quantity * unitPrice, "pricing_profile");
    }

    private Quote quote(long id, long companyId, List<QuoteLineItem> items) {
        try {
            String itemsJson = objectMapper.writeValueAsString(items);
            double total = items.stream().mapToDouble(QuoteLineItem::totalPrice).sum();
            Quote quote = new Quote(companyId, 1L, 2L, "PLN", itemsJson, total, total, 0.8, "test", "[]");
            ReflectionTestUtils.setField(quote, "id", id);
            return quote;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
