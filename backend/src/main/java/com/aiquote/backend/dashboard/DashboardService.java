package com.aiquote.backend.dashboard;

import com.aiquote.backend.conversation.ConversationRepository;
import com.aiquote.backend.conversation.ConversationType;
import com.aiquote.backend.dashboard.DashboardRange.Bounds;
import com.aiquote.backend.feedback.QuoteFeedbackRepository;
import com.aiquote.backend.lead.LeadRepository;
import com.aiquote.backend.lead.LeadStatus;
import com.aiquote.backend.offer.OfferRepository;
import com.aiquote.backend.offer.OfferStatus;
import com.aiquote.backend.quote.QuoteRepository;
import com.aiquote.backend.quote.QuoteStatus;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Composes the owner-facing dashboard (Etap 18) purely from repository-level
 * aggregates (COUNT/SUM/AVG/GROUP BY) — nothing here ever loads a full entity list just
 * to count or sum it in Java; see each repository's own javadoc for the specific query.
 * Every query is explicitly scoped by companyId, matching the tenant-isolation
 * discipline used everywhere else in this codebase.
 *
 * "pendingApproval" is deliberately not date-ranged: it's the live queue depth ("how
 * many need my attention right now"), not a historical count, so it stays constant
 * across the TODAY/LAST_7_DAYS/LAST_30_DAYS tabs by design.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final Set<QuoteStatus> PENDING_APPROVAL_STATUSES = Set.of(QuoteStatus.WAITING_FOR_OWNER, QuoteStatus.OWNER_EDITED);

    private final ConversationRepository conversationRepository;
    private final LeadRepository leadRepository;
    private final QuoteRepository quoteRepository;
    private final OfferRepository offerRepository;
    private final QuoteFeedbackRepository feedbackRepository;

    public DashboardResponse getDashboard(Long companyId, DashboardRange range) {
        Bounds bounds = range.resolve(ZoneId.systemDefault());

        long inquiries = conversationRepository.countByCompanyIdAndTypeAndCreatedAtBetween(
                companyId, ConversationType.CLIENT_QUOTE, bounds.from(), bounds.to());
        long newLeads = leadRepository.countByCompanyIdAndCreatedAtBetween(companyId, bounds.from(), bounds.to());
        long quotesGenerated = quoteRepository.countByCompanyIdAndCreatedAtBetween(companyId, bounds.from(), bounds.to());
        long pendingApproval = quoteRepository.countByCompanyIdAndStatusIn(companyId, PENDING_APPROVAL_STATUSES);
        long offersSent = offerRepository.countByCompanyIdAndStatusAndSentAtBetween(companyId, OfferStatus.SENT, bounds.from(), bounds.to());
        long won = leadRepository.countByCompanyIdAndStatusAndCreatedAtBetween(companyId, LeadStatus.WON, bounds.from(), bounds.to());
        long lost = leadRepository.countByCompanyIdAndStatusAndCreatedAtBetween(companyId, LeadStatus.LOST, bounds.from(), bounds.to());

        long sentQuotesForLeadsInRange =
                quoteRepository.countByStatusForLeadsCreatedBetween(companyId, QuoteStatus.SENT_TO_CLIENT, bounds.from(), bounds.to());

        Double inquiryToLeadPercent = percent(newLeads, inquiries);
        Double leadToSentQuotePercent = percent(sentQuotesForLeadsInRange, newLeads);
        Double sentQuoteToWonPercent = percent(won, sentQuotesForLeadsInRange);

        List<CurrencyAmount> sentOffersTotal = toCurrencyAmounts(offerRepository.sumSentOffersByCurrency(companyId, bounds.from(), bounds.to()));

        List<Object[]> wonOfferRows = offerRepository.sumOffersByCurrencyForLeadsWithStatus(companyId, LeadStatus.WON, bounds.from(), bounds.to());
        List<CurrencyAmount> wonOffersTotal = toCurrencyAmounts(wonOfferRows);
        List<CurrencyAmount> averageWonOfferValue = toCurrencyAverages(wonOfferRows);

        Double averageAiDiffPercentage = feedbackRepository.averageDiffPercentage(companyId, bounds.from(), bounds.to());
        long feedbackCount = feedbackRepository.countByCompanyIdAndCreatedAtBetween(companyId, bounds.from(), bounds.to());
        long changedCount = feedbackRepository.countByCompanyIdAndDiffAmountNotAndCreatedAtBetween(companyId, 0.0, bounds.from(), bounds.to());
        Double percentQuotesChangedByOwner = percent(changedCount, feedbackCount);

        return new DashboardResponse(
                range.name(),
                inquiries,
                newLeads,
                quotesGenerated,
                pendingApproval,
                offersSent,
                won,
                lost,
                inquiryToLeadPercent,
                leadToSentQuotePercent,
                sentQuoteToWonPercent,
                sentOffersTotal,
                wonOffersTotal,
                averageWonOfferValue,
                averageAiDiffPercentage,
                percentQuotesChangedByOwner);
    }

    /** Null (never 0.0) when the denominator is 0 — "not enough data" per Etap 18 #4. */
    private Double percent(long numerator, long denominator) {
        return denominator == 0 ? null : (numerator * 100.0) / denominator;
    }

    private List<CurrencyAmount> toCurrencyAmounts(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new CurrencyAmount((String) row[0], ((Number) row[1]).doubleValue()))
                .toList();
    }

    private List<CurrencyAmount> toCurrencyAverages(List<Object[]> rows) {
        return rows.stream()
                .map(row -> {
                    double sum = ((Number) row[1]).doubleValue();
                    long count = ((Number) row[2]).longValue();
                    return new CurrencyAmount((String) row[0], count == 0 ? 0.0 : sum / count);
                })
                .toList();
    }
}
