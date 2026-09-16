package com.aiquote.backend.quote;

import com.aiquote.backend.lead.Lead;
import com.aiquote.backend.lead.LeadRepository;
import com.aiquote.backend.lead.LeadStatus;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Joins Lead + Quote for the owner-facing leads panel (Etap 14) — lives here, not in
 * the lead package, because quote is allowed to depend on lead (never the reverse; see
 * LeadService's own class javadoc). A single company's lead/quote counts are small
 * enough that two indexed company-scoped queries plus an in-memory join is simpler and
 * plenty fast; no custom JPQL join needed.
 */
@Service
@RequiredArgsConstructor
public class LeadOverviewService {

    private static final Map<LeadOverviewFilter, BiPredicate<Lead, Quote>> MATCHERS = Map.of(
            LeadOverviewFilter.ALL, (lead, quote) -> true,
            LeadOverviewFilter.NEW, (lead, quote) -> lead.getStatus() == LeadStatus.NEW,
            LeadOverviewFilter.AWAITING_QUOTE, (lead, quote) -> quote == null,
            LeadOverviewFilter.AWAITING_APPROVAL, (lead, quote) ->
                    quote != null && (quote.getStatus() == QuoteStatus.WAITING_FOR_OWNER || quote.getStatus() == QuoteStatus.OWNER_EDITED),
            LeadOverviewFilter.SENT, (lead, quote) -> quote != null && quote.getStatus() == QuoteStatus.SENT_TO_CLIENT,
            LeadOverviewFilter.WON, (lead, quote) -> lead.getStatus() == LeadStatus.WON,
            LeadOverviewFilter.LOST, (lead, quote) -> lead.getStatus() == LeadStatus.LOST);

    private final LeadRepository leadRepository;
    private final QuoteRepository quoteRepository;

    public List<LeadOverviewResponse> listForCompany(Long companyId, LeadOverviewFilter filter) {
        List<Lead> leads = leadRepository.findByCompanyIdOrderByCreatedAtDesc(companyId);
        Map<Long, Quote> quoteByLeadId = quoteRepository.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .collect(Collectors.toMap(Quote::getLeadId, quote -> quote, (a, b) -> a));

        BiPredicate<Lead, Quote> matcher = MATCHERS.getOrDefault(filter, MATCHERS.get(LeadOverviewFilter.ALL));

        return leads.stream()
                .filter(lead -> matcher.test(lead, quoteByLeadId.get(lead.getId())))
                .map(lead -> toResponse(lead, quoteByLeadId.get(lead.getId())))
                .toList();
    }

    private LeadOverviewResponse toResponse(Lead lead, Quote quote) {
        boolean awaitingApproval = quote != null
                && (quote.getStatus() == QuoteStatus.WAITING_FOR_OWNER || quote.getStatus() == QuoteStatus.OWNER_EDITED);
        return new LeadOverviewResponse(
                lead.getId(),
                lead.getClientName(),
                lead.getClientPhone(),
                lead.getClientEmail(),
                lead.getAiSummary(),
                lead.getStatus().name(),
                lead.getCreatedAt(),
                quote != null ? quote.getId() : null,
                quote != null ? quote.getStatus().name() : null,
                quote != null ? quote.getTotal() : null,
                awaitingApproval);
    }
}
