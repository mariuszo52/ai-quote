package com.aiquote.backend.dashboard;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * The dashboard's three date-range tabs (Etap 18 #1). Resolved in the JVM's default
 * zone (same convention as the PDF generators' own date formatting) — "today" means
 * the calendar day in that zone, not a rolling 24h window. LAST_7_DAYS/LAST_30_DAYS are
 * inclusive of today, so LAST_7_DAYS spans today and the 6 days before it.
 */
public enum DashboardRange {
    TODAY(0),
    LAST_7_DAYS(6),
    LAST_30_DAYS(29);

    private final int daysBack;

    DashboardRange(int daysBack) {
        this.daysBack = daysBack;
    }

    public static DashboardRange parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return TODAY;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return TODAY;
        }
    }

    public Bounds resolve(ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        Instant from = today.minusDays(daysBack).atStartOfDay(zone).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(zone).toInstant();
        return new Bounds(from, to);
    }

    public record Bounds(Instant from, Instant to) {
    }
}
