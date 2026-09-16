package com.aiquote.backend.knowledgebase;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * One priced service/offering, generic across industries — no field here should ever
 * need an industry-specific name or shape. All fields except {@code name} are nullable:
 * a null value means "not stated", never "zero" or "none" (see PricingProfileMerger).
 *
 * Doubles as both the internal storage shape and the manual-edit API request/response
 * shape — one record, no separate DTO/mapping layer for something this flat.
 */
public record PricingServiceEntry(
        @NotBlank(message = "Nazwa usługi jest wymagana") String name,
        String pricingModel,
        String unit,
        Double basePrice,
        Double minPrice,
        Double maxPrice,
        Double minimumCharge,
        List<String> factors,
        Boolean requiresIndividualQuote,
        String notes) {
}
