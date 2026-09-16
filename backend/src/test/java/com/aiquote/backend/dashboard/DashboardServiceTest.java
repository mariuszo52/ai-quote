package com.aiquote.backend.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiquote.backend.conversation.ConversationRepository;
import com.aiquote.backend.conversation.ConversationType;
import com.aiquote.backend.feedback.QuoteFeedbackRepository;
import com.aiquote.backend.lead.LeadRepository;
import com.aiquote.backend.lead.LeadStatus;
import com.aiquote.backend.offer.OfferRepository;
import com.aiquote.backend.offer.OfferStatus;
import com.aiquote.backend.quote.QuoteRepository;
import com.aiquote.backend.quote.QuoteStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Covers Etap 18's aggregation composition and the "not enough data" contract — every
 * percentage/average must be null (not 0) when its denominator is zero. Individual
 * repository queries are trusted (they're plain COUNT/SUM/AVG); this test verifies
 * DashboardService wires them together and every call is scoped to the given companyId.
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private LeadRepository leadRepository;
    @Mock
    private QuoteRepository quoteRepository;
    @Mock
    private OfferRepository offerRepository;
    @Mock
    private QuoteFeedbackRepository feedbackRepository;

    private DashboardService service;

    @BeforeEach
    void setUp() {
        service = new DashboardService(conversationRepository, leadRepository, quoteRepository, offerRepository, feedbackRepository);
    }

    @Test
    void computesConversionPercentagesFromCounts() {
        when(conversationRepository.countByCompanyIdAndTypeAndCreatedAtBetween(eq(5L), eq(ConversationType.CLIENT_QUOTE), any(), any()))
                .thenReturn(20L);
        when(leadRepository.countByCompanyIdAndCreatedAtBetween(eq(5L), any(), any())).thenReturn(10L);
        when(quoteRepository.countByStatusForLeadsCreatedBetween(eq(5L), eq(QuoteStatus.SENT_TO_CLIENT), any(), any())).thenReturn(4L);
        when(leadRepository.countByCompanyIdAndStatusAndCreatedAtBetween(eq(5L), eq(LeadStatus.WON), any(), any())).thenReturn(2L);
        when(offerRepository.sumSentOffersByCurrency(eq(5L), any(), any())).thenReturn(List.of());
        when(offerRepository.sumOffersByCurrencyForLeadsWithStatus(eq(5L), eq(LeadStatus.WON), any(), any())).thenReturn(List.of());

        DashboardResponse response = service.getDashboard(5L, DashboardRange.TODAY);

        assertThat(response.inquiryToLeadPercent()).isEqualTo(50.0); // 10/20
        assertThat(response.leadToSentQuotePercent()).isEqualTo(40.0); // 4/10
        assertThat(response.sentQuoteToWonPercent()).isEqualTo(50.0); // 2/4
    }

    @Test
    void returnsNullPercentagesInsteadOfZeroWhenThereIsNoData() {
        when(conversationRepository.countByCompanyIdAndTypeAndCreatedAtBetween(any(), any(), any(), any())).thenReturn(0L);
        when(leadRepository.countByCompanyIdAndCreatedAtBetween(any(), any(), any())).thenReturn(0L);
        when(quoteRepository.countByStatusForLeadsCreatedBetween(any(), any(), any(), any())).thenReturn(0L);
        when(offerRepository.sumSentOffersByCurrency(any(), any(), any())).thenReturn(List.of());
        when(offerRepository.sumOffersByCurrencyForLeadsWithStatus(any(), any(), any(), any())).thenReturn(List.of());
        when(feedbackRepository.averageDiffPercentage(any(), any(), any())).thenReturn(null);
        when(feedbackRepository.countByCompanyIdAndCreatedAtBetween(any(), any(), any())).thenReturn(0L);

        DashboardResponse response = service.getDashboard(5L, DashboardRange.TODAY);

        assertThat(response.inquiryToLeadPercent()).isNull();
        assertThat(response.leadToSentQuotePercent()).isNull();
        assertThat(response.sentQuoteToWonPercent()).isNull();
        assertThat(response.averageAiDiffPercentage()).isNull();
        assertThat(response.percentQuotesChangedByOwner()).isNull();
    }

    @Test
    void groupsOfferSumsByCurrency() {
        stubZeroCounts();
        List<Object[]> sentOfferRows = List.<Object[]>of(new Object[] {"PLN", 1500.0, 3L}, new Object[] {"EUR", 200.0, 1L});
        List<Object[]> wonOfferRows = List.<Object[]>of(new Object[] {"PLN", 1000.0, 2L});
        when(offerRepository.sumSentOffersByCurrency(eq(5L), any(), any())).thenReturn(sentOfferRows);
        when(offerRepository.sumOffersByCurrencyForLeadsWithStatus(eq(5L), eq(LeadStatus.WON), any(), any())).thenReturn(wonOfferRows);

        DashboardResponse response = service.getDashboard(5L, DashboardRange.TODAY);

        assertThat(response.sentOffersTotal()).containsExactlyInAnyOrder(
                new CurrencyAmount("PLN", 1500.0), new CurrencyAmount("EUR", 200.0));
        assertThat(response.wonOffersTotal()).containsExactly(new CurrencyAmount("PLN", 1000.0));
        assertThat(response.averageWonOfferValue()).containsExactly(new CurrencyAmount("PLN", 500.0)); // 1000/2
    }

    @Test
    void everyRepositoryCallIsScopedToTheGivenCompanyId() {
        stubZeroCounts();

        service.getDashboard(42L, DashboardRange.LAST_7_DAYS);

        verify(conversationRepository).countByCompanyIdAndTypeAndCreatedAtBetween(eq(42L), any(), any(), any());
        verify(leadRepository).countByCompanyIdAndCreatedAtBetween(eq(42L), any(), any());
        verify(quoteRepository).countByCompanyIdAndCreatedAtBetween(eq(42L), any(), any());
        verify(quoteRepository).countByCompanyIdAndStatusIn(eq(42L), any());
        verify(offerRepository).countByCompanyIdAndStatusAndSentAtBetween(eq(42L), eq(OfferStatus.SENT), any(), any());
        verify(feedbackRepository).averageDiffPercentage(eq(42L), any(), any());
    }

    @Test
    void pendingApprovalIsNotDateRangedAndReflectsCurrentQueueDepth() {
        stubZeroCounts();
        when(quoteRepository.countByCompanyIdAndStatusIn(eq(5L), any())).thenReturn(7L);

        DashboardResponse today = service.getDashboard(5L, DashboardRange.TODAY);
        DashboardResponse last30 = service.getDashboard(5L, DashboardRange.LAST_30_DAYS);

        assertThat(today.pendingApproval()).isEqualTo(7L);
        assertThat(last30.pendingApproval()).isEqualTo(7L);
    }

    private void stubZeroCounts() {
        when(conversationRepository.countByCompanyIdAndTypeAndCreatedAtBetween(any(), any(), any(), any())).thenReturn(0L);
        when(leadRepository.countByCompanyIdAndCreatedAtBetween(any(), any(), any())).thenReturn(0L);
        when(leadRepository.countByCompanyIdAndStatusAndCreatedAtBetween(any(), any(), any(), any())).thenReturn(0L);
        when(quoteRepository.countByCompanyIdAndCreatedAtBetween(any(), any(), any())).thenReturn(0L);
        when(quoteRepository.countByStatusForLeadsCreatedBetween(any(), any(), any(), any())).thenReturn(0L);
        when(offerRepository.countByCompanyIdAndStatusAndSentAtBetween(any(), any(), any(), any())).thenReturn(0L);
        when(offerRepository.sumSentOffersByCurrency(any(), any(), any())).thenReturn(List.of());
        when(offerRepository.sumOffersByCurrencyForLeadsWithStatus(any(), any(), any(), any())).thenReturn(List.of());
        when(feedbackRepository.averageDiffPercentage(any(), any(), any())).thenReturn(null);
        when(feedbackRepository.countByCompanyIdAndCreatedAtBetween(any(), any(), any())).thenReturn(0L);
    }
}
