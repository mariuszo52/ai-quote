package com.aiquote.backend.quote;

import com.aiquote.backend.tenant.TenantContext;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Owns the leads list (GET /api/leads, bare) instead of lead.LeadController — the
 * panel needs each lead's quote status/total joined in (Etap 14 #4), and quote is
 * allowed to depend on lead (never the reverse; see LeadService's class javadoc), so
 * this join has to be assembled from the quote side. lead.LeadController still owns
 * everything genuinely lead-only: GET/PATCH a single lead, attachments.
 */
@RestController
@RequestMapping("/api/leads")
@RequiredArgsConstructor
public class LeadOverviewController {

    private final LeadOverviewService leadOverviewService;

    @GetMapping
    public List<LeadOverviewResponse> list(@RequestParam(required = false) String filter) {
        return leadOverviewService.listForCompany(TenantContext.currentCompanyId(), LeadOverviewFilter.parse(filter));
    }
}
