package com.aiquote.backend.billing;

import java.time.Instant;

/**
 * Flat projection of whatever a Stripe webhook told us, decoupling BillingService from
 * Stripe's own object model (Event/Subscription/Session) — mirrors the same reasoning
 * as AiTurnResult decoupling callers from the raw Anthropic response shape. Fields are
 * null when not applicable to the event's {@link #type()}.
 */
public record StripeWebhookEvent(
        String type,
        String customerId,
        String subscriptionId,
        String priceId,
        String subscriptionState,
        Instant periodStart,
        Instant periodEnd) {
}
