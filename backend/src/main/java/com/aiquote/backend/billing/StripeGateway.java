package com.aiquote.backend.billing;

/**
 * Wraps the raw Stripe SDK calls (which use static methods and a global Stripe.apiKey —
 * awkward to mock directly) behind a plain interface, so BillingService stays
 * unit-testable via a mock instead of fighting the SDK. StripeGatewayImpl is the only
 * real implementation — same reasoning as the AiClient abstraction over Anthropic.
 */
public interface StripeGateway {

    /** Creates a Stripe Customer for a company that doesn't have one yet. Returns the new customer ID. */
    String createCustomer(String email, String name);

    /** Returns the Checkout Session URL to redirect the browser to. */
    String createCheckoutSessionUrl(String customerId, String priceId, String successUrl, String cancelUrl);

    /** Returns the Billing Portal Session URL to redirect the browser to. */
    String createPortalSessionUrl(String customerId, String returnUrl);

    /**
     * Verifies the webhook signature and returns a flat projection of the event.
     * Throws if the signature doesn't verify — the signature check is the entire
     * security boundary for this one unauthenticated endpoint (see BillingController).
     */
    StripeWebhookEvent constructWebhookEvent(String payload, String signatureHeader);
}
