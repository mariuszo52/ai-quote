package com.aiquote.backend.billing;

import java.time.Instant;

public record BillingStatusResponse(
        String plan,
        String planDisplayName,
        String subscriptionStatus,
        Instant trialEndsAt,
        Instant currentPeriodEnd,
        long quotesUsed,
        int quoteLimit,
        boolean canManageBilling,
        boolean blocked) {
}
