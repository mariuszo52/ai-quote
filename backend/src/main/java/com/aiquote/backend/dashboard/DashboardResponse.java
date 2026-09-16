package com.aiquote.backend.dashboard;

import java.util.List;

/**
 * Every percentage/average field is nullable — null means "not enough data" (Etap 18
 * #4's explicit requirement to never fake a 0%), and the frontend must render that as
 * "Brak wystarczających danych" rather than a number.
 */
public record DashboardResponse(
        String range,
        long inquiries,
        long newLeads,
        long quotesGenerated,
        long pendingApproval,
        long offersSent,
        long won,
        long lost,
        Double inquiryToLeadPercent,
        Double leadToSentQuotePercent,
        Double sentQuoteToWonPercent,
        List<CurrencyAmount> sentOffersTotal,
        List<CurrencyAmount> wonOffersTotal,
        List<CurrencyAmount> averageWonOfferValue,
        Double averageAiDiffPercentage,
        Double percentQuotesChangedByOwner) {
}
