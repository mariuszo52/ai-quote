package com.aiquote.backend.quote;

import com.aiquote.backend.company.Company;
import com.aiquote.backend.company.CompanyService;
import com.aiquote.backend.lead.Lead;
import com.aiquote.backend.lead.LeadNotFoundException;
import com.aiquote.backend.lead.LeadRepository;
import com.aiquote.backend.pdf.QuotePdfGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owner-facing draft quote read/edit/approval. Joins in a few Lead fields (client name/
 * phone/email) purely at response time — Quote itself stores no client data, it's
 * reached via leadId, per Etap 9's "don't duplicate, use the relation" requirement.
 *
 * Etap 10's core rule lives here: totals are ALWAYS recomputed server-side from
 * quantity * unitPrice (QuoteCalculator) — an owner edit's incoming totalPrice is
 * ignored exactly like the AI's was, so the frontend never gets to decide a final sum.
 */
@Service
@RequiredArgsConstructor
public class QuoteService {

    private static final Set<QuoteStatus> APPROVABLE_STATUSES = Set.of(QuoteStatus.WAITING_FOR_OWNER, QuoteStatus.OWNER_EDITED);

    private final QuoteRepository quoteRepository;
    private final LeadRepository leadRepository;
    private final QuoteChangeLogRepository changeLogRepository;
    private final CompanyService companyService;
    private final QuotePdfGenerator quotePdfGenerator;
    private final ObjectMapper objectMapper;

    public List<QuoteResponse> listForCompany(Long companyId) {
        return quoteRepository.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .map(this::toResponse)
                .toList();
    }

    public QuoteResponse getDetail(Long companyId, Long quoteId) {
        return toResponse(requireOwned(companyId, quoteId));
    }

    public QuoteResponse getByLeadId(Long companyId, Long leadId) {
        Quote quote = quoteRepository.findByLeadIdAndCompanyId(leadId, companyId)
                .orElseThrow(() -> new QuoteNotFoundException(leadId));
        return toResponse(quote);
    }

    @Transactional
    public QuoteResponse update(Long companyId, Long quoteId, UpdateQuoteRequest request) {
        Quote quote = requireOwned(companyId, quoteId);
        // Etap 20 fix: SEND_FAILED and SENT_TO_CLIENT are also reachable only after
        // approve() — checking approvedAt (rather than just status == APPROVED) blocks
        // edits for every post-approval status, not just the first one. The Offer is a
        // frozen snapshot of the approved quote (see Offer's class javadoc); an edit
        // that "succeeds" here would silently diverge from what was already sent/would
        // be resent to the client, since resend uses the Offer's own snapshot, not this.
        if (quote.getApprovedAt() != null) {
            throw new InvalidQuoteStatusException("Nie można edytować zaakceptowanej wyceny.");
        }

        List<QuoteLineItem> before = readItems(quote.getItemsJson());
        List<QuoteLineItem> after = request.items().stream().map(QuoteCalculator::withComputedTotal).toList();
        double total = QuoteCalculator.subtotal(after);

        String summary = QuoteChangeSummaryBuilder.build(before, after, quote.getCurrency());
        if (!summary.isBlank()) {
            changeLogRepository.save(new QuoteChangeLogEntry(companyId, quote.getId(), summary));
        }

        quote.applyOwnerEdit(
                writeJson(after),
                total,
                total,
                blankToNull(request.clientNote()),
                blankToNull(request.internalNote()),
                blankToNull(request.estimatedTimeline()),
                request.offerValidUntil());
        return toResponse(quote);
    }

    @Transactional
    public QuoteResponse approve(Long companyId, Long quoteId) {
        Quote quote = requireOwned(companyId, quoteId);
        if (!APPROVABLE_STATUSES.contains(quote.getStatus())) {
            throw new InvalidQuoteStatusException("Tylko wycena oczekująca na akceptację lub poprawiona przez właściciela może zostać zaakceptowana.");
        }
        quote.approve();
        return toResponse(quote);
    }

    public byte[] getPdf(Long companyId, Long quoteId) {
        Quote quote = requireOwned(companyId, quoteId);
        Lead lead = leadRepository.findById(quote.getLeadId())
                .orElseThrow(() -> new LeadNotFoundException(quote.getLeadId()));
        Company company = companyService.getById(companyId);
        return quotePdfGenerator.generate(quote, lead, company);
    }

    private Quote requireOwned(Long companyId, Long quoteId) {
        return quoteRepository.findByIdAndCompanyId(quoteId, companyId)
                .orElseThrow(() -> new QuoteNotFoundException(quoteId));
    }

    private QuoteResponse toResponse(Quote quote) {
        Lead lead = leadRepository.findById(quote.getLeadId()).orElse(null);
        List<QuoteChangeLogResponse> changeLog = changeLogRepository.findByQuoteIdOrderByCreatedAtAsc(quote.getId()).stream()
                .map(entry -> new QuoteChangeLogResponse(entry.getCreatedAt(), entry.getSummary()))
                .toList();

        return new QuoteResponse(
                quote.getId(),
                quote.getCompanyId(),
                quote.getConversationId(),
                quote.getLeadId(),
                quote.getStatus().name(),
                quote.getCurrency(),
                readItems(quote.getItemsJson()),
                quote.getSubtotal(),
                quote.getTotal(),
                quote.getAiConfidence(),
                quote.getAiReasoning(),
                readStringList(quote.getUncertainFactorsJson()),
                readItems(quote.getAiItemsJson()),
                quote.getAiSubtotal(),
                quote.getAiTotal(),
                quote.getClientNote(),
                quote.getInternalNote(),
                quote.getEstimatedTimeline(),
                quote.getOfferValidUntil(),
                quote.getApprovedAt(),
                changeLog,
                quote.getCreatedAt(),
                quote.getUpdatedAt(),
                lead != null ? lead.getClientName() : null,
                lead != null ? lead.getClientPhone() : null,
                lead != null ? lead.getClientEmail() : null);
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private List<QuoteLineItem> readItems(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<QuoteLineItem>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse quote items", e);
        }
    }

    private List<String> readStringList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse quote uncertain factors", e);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize quote items", e);
        }
    }
}
