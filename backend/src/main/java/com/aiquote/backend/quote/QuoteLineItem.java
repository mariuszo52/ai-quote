package com.aiquote.backend.quote;

import jakarta.validation.constraints.NotBlank;

/**
 * One line of a draft quote. unitPrice/totalPrice are nullable on purpose: null means
 * "not established from the company's pricing knowledge or the conversation" — never a
 * guessed number. totalPrice is always recomputed server-side from quantity * unitPrice
 * (see QuoteCalculator), not trusted verbatim from the AI or from an owner edit, so
 * arithmetic is guaranteed correct regardless of what the caller sends.
 *
 * Doubles as the owner-edit API request/response shape too, same reasoning as
 * PricingServiceEntry in the knowledgebase package — one flat record, no separate DTO.
 */
public record QuoteLineItem(
        @NotBlank(message = "Nazwa pozycji jest wymagana") String name,
        String description,
        Double quantity,
        String unit,
        Double unitPrice,
        Double totalPrice,
        String source) {
}
