package com.aiquote.backend.offer;

/**
 * Client-facing line item — deliberately a separate type from quote.QuoteLineItem even
 * though the shape overlaps, so nothing in the offer package can accidentally carry over
 * a Quote-only field (e.g. "source") into what the client sees.
 */
public record OfferLineItem(String name, String description, Double quantity, String unit, Double unitPrice, Double totalPrice) {
}
