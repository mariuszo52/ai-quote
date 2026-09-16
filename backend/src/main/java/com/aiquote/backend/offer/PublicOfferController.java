package com.aiquote.backend.offer;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * Anonymous, unauthenticated access to a final offer via its public token — no login,
 * no company/tenant context, matching the /api/public/** permitAll rule in
 * SecurityConfig. Deliberately depends on nothing but OfferService and only ever
 * returns PublicOfferResponse / the stored offer PDF — there is no code path here that
 * can reach Quote, internal notes, AI reasoning, or any other company's data, since a
 * random/guessed token simply 404s (OfferNotFoundException) rather than enumerating.
 */
@RestController
@RequestMapping("/api/public/offers")
@RequiredArgsConstructor
public class PublicOfferController {

    private final OfferService offerService;

    @GetMapping("/{token}")
    public PublicOfferResponse getOffer(@PathVariable String token) {
        return offerService.getPublicOffer(token);
    }

    @GetMapping("/{token}/pdf")
    public ResponseEntity<byte[]> getOfferPdf(@PathVariable String token) {
        byte[] pdf = offerService.getPublicPdf(token);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"oferta.pdf\"")
                .body(pdf);
    }
}
