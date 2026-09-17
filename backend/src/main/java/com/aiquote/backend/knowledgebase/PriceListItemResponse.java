package com.aiquote.backend.knowledgebase;

import java.time.Instant;

public record PriceListItemResponse(
        Long id,
        String name,
        String category,
        Double price,
        String unit,
        String source,
        Instant createdAt,
        Instant updatedAt) {

    public static PriceListItemResponse from(PriceListItem item) {
        return new PriceListItemResponse(
                item.getId(),
                item.getName(),
                item.getCategory(),
                item.getPrice(),
                item.getUnit(),
                item.getSource().name(),
                item.getCreatedAt(),
                item.getUpdatedAt());
    }
}
