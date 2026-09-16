package com.aiquote.backend.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.aiquote.backend.lead.Lead;
import com.aiquote.backend.lead.LeadRepository;
import com.aiquote.backend.lead.LeadStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Covers Etap 14's leads panel filters and the Lead/Quote join itself — including the
 * "no quote yet" case, which is the whole reason this lives in the quote package
 * (see LeadOverviewController's javadoc) instead of just extending Lead's own list.
 */
@ExtendWith(MockitoExtension.class)
class LeadOverviewServiceTest {

    @Mock
    private LeadRepository leadRepository;
    @Mock
    private QuoteRepository quoteRepository;

    private LeadOverviewService service;

    @BeforeEach
    void setUp() {
        service = new LeadOverviewService(leadRepository, quoteRepository);
    }

    @Test
    void joinsEachLeadWithItsQuoteWhenOneExists() {
        Lead leadWithQuote = lead(1L, LeadStatus.NEW);
        Lead leadWithoutQuote = lead(2L, LeadStatus.NEW);
        Quote quote = quote(10L, 1L, QuoteStatus.WAITING_FOR_OWNER, 250.0);
        when(leadRepository.findByCompanyIdOrderByCreatedAtDesc(5L)).thenReturn(List.of(leadWithQuote, leadWithoutQuote));
        when(quoteRepository.findByCompanyIdOrderByCreatedAtDesc(5L)).thenReturn(List.of(quote));

        List<LeadOverviewResponse> result = service.listForCompany(5L, LeadOverviewFilter.ALL);

        LeadOverviewResponse withQuote = result.stream().filter(r -> r.leadId().equals(1L)).findFirst().orElseThrow();
        LeadOverviewResponse withoutQuote = result.stream().filter(r -> r.leadId().equals(2L)).findFirst().orElseThrow();
        assertThat(withQuote.quoteId()).isEqualTo(10L);
        assertThat(withQuote.quoteTotal()).isEqualTo(250.0);
        assertThat(withQuote.awaitingApproval()).isTrue();
        assertThat(withoutQuote.quoteId()).isNull();
        assertThat(withoutQuote.awaitingApproval()).isFalse();
    }

    @Test
    void filterAwaitingQuoteOnlyReturnsLeadsWithNoQuoteYet() {
        Lead leadWithQuote = lead(1L, LeadStatus.NEW);
        Lead leadWithoutQuote = lead(2L, LeadStatus.NEW);
        Quote quote = quote(10L, 1L, QuoteStatus.WAITING_FOR_OWNER, 250.0);
        when(leadRepository.findByCompanyIdOrderByCreatedAtDesc(5L)).thenReturn(List.of(leadWithQuote, leadWithoutQuote));
        when(quoteRepository.findByCompanyIdOrderByCreatedAtDesc(5L)).thenReturn(List.of(quote));

        List<LeadOverviewResponse> result = service.listForCompany(5L, LeadOverviewFilter.AWAITING_QUOTE);

        assertThat(result).extracting(LeadOverviewResponse::leadId).containsExactly(2L);
    }

    @Test
    void filterAwaitingApprovalMatchesWaitingForOwnerAndOwnerEdited() {
        Lead lead1 = lead(1L, LeadStatus.NEW);
        Lead lead2 = lead(2L, LeadStatus.NEW);
        Lead lead3 = lead(3L, LeadStatus.NEW);
        Quote waiting = quote(10L, 1L, QuoteStatus.WAITING_FOR_OWNER, 100.0);
        Quote edited = quote(11L, 2L, QuoteStatus.OWNER_EDITED, 150.0);
        Quote approved = quote(12L, 3L, QuoteStatus.APPROVED, 200.0);
        when(leadRepository.findByCompanyIdOrderByCreatedAtDesc(5L)).thenReturn(List.of(lead1, lead2, lead3));
        when(quoteRepository.findByCompanyIdOrderByCreatedAtDesc(5L)).thenReturn(List.of(waiting, edited, approved));

        List<LeadOverviewResponse> result = service.listForCompany(5L, LeadOverviewFilter.AWAITING_APPROVAL);

        assertThat(result).extracting(LeadOverviewResponse::leadId).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void filterSentMatchesSentToClientQuotesOnly() {
        Lead lead1 = lead(1L, LeadStatus.QUOTE_SENT);
        Lead lead2 = lead(2L, LeadStatus.NEW);
        Quote sent = quote(10L, 1L, QuoteStatus.SENT_TO_CLIENT, 300.0);
        Quote waiting = quote(11L, 2L, QuoteStatus.WAITING_FOR_OWNER, 100.0);
        when(leadRepository.findByCompanyIdOrderByCreatedAtDesc(5L)).thenReturn(List.of(lead1, lead2));
        when(quoteRepository.findByCompanyIdOrderByCreatedAtDesc(5L)).thenReturn(List.of(sent, waiting));

        List<LeadOverviewResponse> result = service.listForCompany(5L, LeadOverviewFilter.SENT);

        assertThat(result).extracting(LeadOverviewResponse::leadId).containsExactly(1L);
    }

    @Test
    void filterWonAndLostMatchLeadStatusRegardlessOfQuote() {
        Lead won = lead(1L, LeadStatus.WON);
        Lead lost = lead(2L, LeadStatus.LOST);
        Lead neither = lead(3L, LeadStatus.NEW);
        when(leadRepository.findByCompanyIdOrderByCreatedAtDesc(5L)).thenReturn(List.of(won, lost, neither));
        when(quoteRepository.findByCompanyIdOrderByCreatedAtDesc(5L)).thenReturn(List.of());

        assertThat(service.listForCompany(5L, LeadOverviewFilter.WON)).extracting(LeadOverviewResponse::leadId).containsExactly(1L);
        assertThat(service.listForCompany(5L, LeadOverviewFilter.LOST)).extracting(LeadOverviewResponse::leadId).containsExactly(2L);
    }

    @Test
    void filterNewMatchesOnlyNewLeads() {
        Lead newLead = lead(1L, LeadStatus.NEW);
        Lead contacted = lead(2L, LeadStatus.CONTACTED);
        when(leadRepository.findByCompanyIdOrderByCreatedAtDesc(5L)).thenReturn(List.of(newLead, contacted));
        when(quoteRepository.findByCompanyIdOrderByCreatedAtDesc(5L)).thenReturn(List.of());

        List<LeadOverviewResponse> result = service.listForCompany(5L, LeadOverviewFilter.NEW);

        assertThat(result).extracting(LeadOverviewResponse::leadId).containsExactly(1L);
    }

    @Test
    void isTenantScopedByOnlyQueryingTheGivenCompanyId() {
        when(leadRepository.findByCompanyIdOrderByCreatedAtDesc(999L)).thenReturn(List.of());
        when(quoteRepository.findByCompanyIdOrderByCreatedAtDesc(999L)).thenReturn(List.of());

        List<LeadOverviewResponse> result = service.listForCompany(999L, LeadOverviewFilter.ALL);

        assertThat(result).isEmpty();
    }

    @Test
    void parseFallsBackToAllForUnknownOrMissingValues() {
        assertThat(LeadOverviewFilter.parse(null)).isEqualTo(LeadOverviewFilter.ALL);
        assertThat(LeadOverviewFilter.parse("")).isEqualTo(LeadOverviewFilter.ALL);
        assertThat(LeadOverviewFilter.parse("not-a-real-filter")).isEqualTo(LeadOverviewFilter.ALL);
        assertThat(LeadOverviewFilter.parse("won")).isEqualTo(LeadOverviewFilter.WON);
    }

    private Lead lead(long id, LeadStatus status) {
        Lead lead = new Lead(5L, 3L, "Klient " + id, "600100200", null, "Opis", null, null, "PLN", null);
        ReflectionTestUtils.setField(lead, "id", id);
        ReflectionTestUtils.setField(lead, "status", status);
        return lead;
    }

    private Quote quote(long id, long leadId, QuoteStatus status, double total) {
        Quote quote = new Quote(5L, 3L, leadId, "PLN", "[]", total, total, 0.8, "test", "[]");
        ReflectionTestUtils.setField(quote, "id", id);
        ReflectionTestUtils.setField(quote, "status", status);
        return quote;
    }
}
