package com.aiquote.backend.quote;

/**
 * Backs the leads panel's filter chips (Etap 14 #4). Matching happens against the
 * already-joined LeadOverviewResponse row, not raw entities — see
 * LeadOverviewService#matches.
 */
public enum LeadOverviewFilter {
    ALL,
    NEW,
    AWAITING_QUOTE,
    AWAITING_APPROVAL,
    SENT,
    WON,
    LOST;

    public static LeadOverviewFilter parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return ALL;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ALL;
        }
    }
}
