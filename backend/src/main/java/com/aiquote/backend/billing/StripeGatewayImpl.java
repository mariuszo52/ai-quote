package com.aiquote.backend.billing;

import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.billingportal.SessionCreateParams.Builder;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Etap: SaaS billing follow-up — StripeException's own message (e.g. "No such price:
 * '...'") is the one piece of information that actually explains a checkout/portal
 * failure, but BillingException (an ApiException) is never logged by
 * GlobalExceptionHandler — only truly unhandled exceptions are. Without logging it
 * here, a misconfigured Price ID or API key fails "cleanly" for the client but
 * invisibly for whoever has to debug it from the server logs. */
@Slf4j
@Component
public class StripeGatewayImpl implements StripeGateway {

    private final String webhookSecret;

    public StripeGatewayImpl(
            @Value("${app.stripe.secret-key}") String secretKey,
            @Value("${app.stripe.webhook-secret}") String webhookSecret) {
        // Stripe's SDK is designed around this single global field rather than a
        // per-instance client — blank in local dev (no key configured yet) is harmless
        // here and only surfaces as a clean BillingException the moment something
        // actually tries to call Stripe (see the catch blocks below).
        Stripe.apiKey = secretKey;
        this.webhookSecret = webhookSecret;
    }

    @Override
    public String createCustomer(String email, String name) {
        try {
            CustomerCreateParams params = CustomerCreateParams.builder()
                    .setEmail(email)
                    .setName(name)
                    .build();
            return Customer.create(params).getId();
        } catch (StripeException e) {
            log.error("Stripe createCustomer failed: {}", e.getMessage(), e);
            throw new BillingException("Nie udało się utworzyć klienta w Stripe.", e);
        }
    }

    @Override
    public String createCheckoutSessionUrl(String customerId, String priceId, String successUrl, String cancelUrl) {
        try {
            com.stripe.param.checkout.SessionCreateParams params = com.stripe.param.checkout.SessionCreateParams.builder()
                    .setMode(com.stripe.param.checkout.SessionCreateParams.Mode.SUBSCRIPTION)
                    .setCustomer(customerId)
                    .addLineItem(com.stripe.param.checkout.SessionCreateParams.LineItem.builder()
                            .setPrice(priceId)
                            .setQuantity(1L)
                            .build())
                    .setSuccessUrl(successUrl)
                    .setCancelUrl(cancelUrl)
                    .build();
            return Session.create(params).getUrl();
        } catch (StripeException e) {
            log.error("Stripe createCheckoutSession failed for priceId={}: {}", priceId, e.getMessage(), e);
            throw new BillingException("Nie udało się utworzyć sesji płatności Stripe.", e);
        }
    }

    @Override
    public String createPortalSessionUrl(String customerId, String returnUrl) {
        try {
            Builder params = com.stripe.param.billingportal.SessionCreateParams.builder()
                    .setCustomer(customerId)
                    .setReturnUrl(returnUrl);
            return com.stripe.model.billingportal.Session.create(params.build()).getUrl();
        } catch (StripeException e) {
            log.error("Stripe createPortalSession failed: {}", e.getMessage(), e);
            throw new BillingException("Nie udało się otworzyć panelu rozliczeń Stripe.", e);
        }
    }

    @Override
    public StripeWebhookEvent constructWebhookEvent(String payload, String signatureHeader) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, signatureHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.error("Stripe webhook signature verification failed: {}", e.getMessage(), e);
            throw new BillingException("Nieprawidłowy podpis webhooka Stripe.", e);
        }

        StripeObject dataObject = event.getDataObjectDeserializer().getObject().orElse(null);
        Subscription subscription = resolveSubscription(dataObject);

        if (subscription == null) {
            return new StripeWebhookEvent(event.getType(), null, null, null, null, null, null);
        }

        // current_period_start/end live on the subscription item, not the subscription
        // itself, as of this SDK's API version (confirmed against the actual jar).
        SubscriptionItem item = subscription.getItems().getData().get(0);
        return new StripeWebhookEvent(
                event.getType(),
                subscription.getCustomer(),
                subscription.getId(),
                item.getPrice().getId(),
                subscription.getStatus(),
                Instant.ofEpochSecond(item.getCurrentPeriodStart()),
                Instant.ofEpochSecond(item.getCurrentPeriodEnd()));
    }

    /** Subscription events carry the Subscription directly; checkout.session.completed
     * only carries the Session, so the full Subscription (price/period/status) has to
     * be fetched separately. */
    private Subscription resolveSubscription(StripeObject dataObject) {
        if (dataObject instanceof Subscription subscription) {
            return subscription;
        }
        if (dataObject instanceof Session checkoutSession && checkoutSession.getSubscription() != null) {
            try {
                return Subscription.retrieve(checkoutSession.getSubscription());
            } catch (StripeException e) {
                log.error("Stripe subscription retrieve failed: {}", e.getMessage(), e);
                throw new BillingException("Nie udało się pobrać subskrypcji Stripe.", e);
            }
        }
        return null;
    }
}
