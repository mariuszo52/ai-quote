package com.aiquote.backend.offer;

import com.aiquote.backend.quote.QuoteResponse;
import com.aiquote.backend.tenant.TenantContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * Owner-facing (authenticated, tenant-scoped) endpoints for the final client offer.
 * POST /api/quotes/{id}/approve lives here rather than on QuoteController — see that
 * class's javadoc for why: offer depends on quote, so the approve+generate-offer
 * orchestration has to be triggered from this side to avoid a package cycle.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OfferController {

    private final OfferService offerService;
    private final OfferSendService offerSendService;

    @PostMapping("/quotes/{id}/approve")
    public QuoteResponse approve(@PathVariable Long id) {
        return offerService.approveQuoteAndGenerateOffer(TenantContext.currentCompanyId(), id);
    }

    @GetMapping("/quotes/{id}/offer")
    public OfferResponse getOffer(@PathVariable Long id) {
        return offerService.getByQuoteId(TenantContext.currentCompanyId(), id);
    }

    @GetMapping("/quotes/{id}/offer/pdf")
    public ResponseEntity<byte[]> getOfferPdf(@PathVariable Long id) {
        byte[] pdf = offerService.getPdf(TenantContext.currentCompanyId(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"oferta-" + id + ".pdf\"")
                .body(pdf);
    }

    @PostMapping("/quotes/{id}/offer/send")
    public OfferResponse sendOffer(@PathVariable Long id) {
        return offerSendService.sendOffer(TenantContext.currentCompanyId(), id);
    }
}
