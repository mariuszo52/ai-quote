package com.aiquote.backend.feedback;

/** Optional, owner-supplied reason for why the accepted quote differs from AI's
 * proposal (Etap 15) — purely descriptive data for future pricing-profile tuning, never
 * consumed automatically. */
public enum FeedbackReason {
    MISSING_ITEM,
    PRICE_TOO_LOW,
    PRICE_TOO_HIGH,
    EXTRA_DIFFICULTY,
    UNUSUAL_CONDITIONS,
    MATERIALS,
    LABOR,
    OTHER
}
