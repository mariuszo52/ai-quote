package com.aiquote.backend.knowledgebase;

import jakarta.validation.constraints.NotBlank;

public record PriceListItemRequest(
        @NotBlank(message = "Nazwa materiału jest wymagana") String name,
        String category,
        Double price,
        String unit) {
}
