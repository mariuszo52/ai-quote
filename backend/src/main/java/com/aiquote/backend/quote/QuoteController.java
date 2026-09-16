package com.aiquote.backend.quote;

import com.aiquote.backend.tenant.TenantContext;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Owner-facing (authenticated, tenant-scoped) draft quote endpoints. Kept separate from
 * PublicQuoteController, which serves the anonymous client chat under a per-conversation
 * token instead of a JWT.
 *
 * The lead-scoped lookup lives here as /api/quotes/by-lead/{leadId} rather than under
 * /api/leads — LeadService/LeadController deliberately never depend on the quote
 * package (see LeadService's class javadoc) since quote already depends on lead, and a
 * dependency back the other way would create a package cycle.
 *
 * POST /api/quotes/{id}/approve deliberately lives on OfferController, not here — same
 * cycle-avoidance reasoning: approving must also generate the final Offer (Etap 12), and
 * offer already depends on quote, so the orchestration has to happen from that side.
 */
@RestController
@RequestMapping("/api/quotes")
@RequiredArgsConstructor
public class QuoteController {

    private final QuoteService quoteService;

    @GetMapping
    public List<QuoteResponse> list() {
        return quoteService.listForCompany(TenantContext.currentCompanyId());
    }

    @GetMapping("/{id}")
    public QuoteResponse detail(@PathVariable Long id) {
        return quoteService.getDetail(TenantContext.currentCompanyId(), id);
    }

    @GetMapping("/by-lead/{leadId}")
    public QuoteResponse byLead(@PathVariable Long leadId) {
        return quoteService.getByLeadId(TenantContext.currentCompanyId(), leadId);
    }

    @PutMapping("/{id}")
    public QuoteResponse update(@PathVariable Long id, @Valid @RequestBody UpdateQuoteRequest request) {
        return quoteService.update(TenantContext.currentCompanyId(), id, request);
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        byte[] pdf = quoteService.getPdf(TenantContext.currentCompanyId(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"wycena-" + id + ".pdf\"")
                .body(pdf);
    }
}
