package com.aiquote.backend.knowledgebase;

import java.util.List;

/**
 * The structured shape stored in CompanyPricingProfile.profileJson — a list of priced
 * services plus free-form notes that don't belong to any single service (e.g. travel
 * fees, general terms). Deliberately flat and industry-agnostic.
 */
public record PricingProfileData(List<PricingServiceEntry> services, String generalNotes) {

    public static PricingProfileData empty() {
        return new PricingProfileData(List.of(), null);
    }
}
