package com.aiquote.backend.billing;

import com.aiquote.backend.auth.AppUser;
import com.aiquote.backend.auth.AppUserRepository;
import com.aiquote.backend.company.Company;
import com.aiquote.backend.company.CompanyPlan;
import com.aiquote.backend.company.CompanyRepository;
import com.aiquote.backend.company.CompanyService;
import com.aiquote.backend.company.SubscriptionStatus;
import com.aiquote.backend.quote.QuoteRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the trial -> paid-plan lifecycle: reading usage/status for the owner-facing
 * billing page, starting a Stripe Checkout session for a chosen plan, opening the
 * Stripe Billing Portal for an existing subscriber, and applying whatever a Stripe
 * webhook reports back. Sits above company/quote (like the dashboard package sits
 * above conversation/lead/quote/offer/feedback) — depends on both, neither depends
 * back, so no cycle.
 */
@Service
public class BillingService {

    private final CompanyRepository companyRepository;
    private final CompanyService companyService;
    private final AppUserRepository appUserRepository;
    private final QuoteRepository quoteRepository;
    private final StripeGateway stripeGateway;
    private final String frontendUrl;
    private final String priceStarter;
    private final String priceGrowth;
    private final String pricePro;

    public BillingService(
            CompanyRepository companyRepository,
            CompanyService companyService,
            AppUserRepository appUserRepository,
            QuoteRepository quoteRepository,
            StripeGateway stripeGateway,
            @Value("${app.frontend-url}") String frontendUrl,
            @Value("${app.stripe.price-starter}") String priceStarter,
            @Value("${app.stripe.price-growth}") String priceGrowth,
            @Value("${app.stripe.price-pro}") String pricePro) {
        this.companyRepository = companyRepository;
        this.companyService = companyService;
        this.appUserRepository = appUserRepository;
        this.quoteRepository = quoteRepository;
        this.stripeGateway = stripeGateway;
        this.frontendUrl = frontendUrl;
        this.priceStarter = priceStarter;
        this.priceGrowth = priceGrowth;
        this.pricePro = pricePro;
    }

    public BillingStatusResponse getStatus(Long companyId) {
        Company company = companyService.getById(companyId);
        long quotesUsed = quoteRepository.countByCompanyIdAndCreatedAtBetween(
                companyId, company.currentPeriodStart(), company.currentPeriodEnd());

        // Same condition QuoteAgentService.assertWithinPlanLimit blocks new client
        // conversations on — surfaced here so the owner-facing app shell can lock the
        // rest of the app and point the owner at plan selection instead of just letting
        // clients silently get turned away with no visible cause.
        boolean blocked = company.isTrialExpired() || quotesUsed >= company.quoteLimit();

        return new BillingStatusResponse(
                company.getPlan().name(),
                company.getPlan().displayName(),
                company.getSubscriptionStatus().name(),
                company.isOnTrial() ? company.currentPeriodEnd() : null,
                company.currentPeriodEnd(),
                quotesUsed,
                company.quoteLimit(),
                company.getStripeCustomerId() != null,
                blocked);
    }

    @Transactional
    public RedirectUrlResponse createCheckoutSession(Long companyId, CompanyPlan plan) {
        Company company = companyService.getById(companyId);
        String customerId = company.getStripeCustomerId();
        if (customerId == null) {
            String ownerEmail = appUserRepository.findFirstByCompanyIdOrderByIdAsc(companyId)
                    .map(AppUser::getEmail)
                    .orElseThrow(() -> new BillingException("Nie znaleziono konta właściciela firmy."));
            customerId = stripeGateway.createCustomer(ownerEmail, company.getName());
            company.applySubscription(company.getPlan(), customerId, null, company.getSubscriptionStatus(), null, null);
        }

        String url = stripeGateway.createCheckoutSessionUrl(
                customerId,
                priceIdFor(plan),
                frontendUrl + "/app/billing?checkout=success",
                frontendUrl + "/app/billing?checkout=cancelled");
        return new RedirectUrlResponse(url);
    }

    public RedirectUrlResponse createPortalSession(Long companyId) {
        Company company = companyService.getById(companyId);
        if (company.getStripeCustomerId() == null) {
            throw new BillingException("Firma nie ma jeszcze aktywnej subskrypcji.");
        }
        String url = stripeGateway.createPortalSessionUrl(company.getStripeCustomerId(), frontendUrl + "/app/billing");
        return new RedirectUrlResponse(url);
    }

    /** Applies whatever the webhook reports — irrelevant event types resolve to a null
     * subscriptionId (see StripeGateway) and are silently ignored, not every Stripe
     * event type needs handling here. */
    @Transactional
    public void handleWebhookEvent(String payload, String signatureHeader) {
        StripeWebhookEvent event = stripeGateway.constructWebhookEvent(payload, signatureHeader);
        if (event.subscriptionId() == null) {
            return;
        }

        Company company = companyRepository.findByStripeCustomerId(event.customerId()).orElse(null);
        if (company == null) {
            return;
        }

        company.applySubscription(
                planForPriceId(event.priceId()),
                event.customerId(),
                event.subscriptionId(),
                mapStatus(event.subscriptionState()),
                event.periodStart(),
                event.periodEnd());
    }

    private String priceIdFor(CompanyPlan plan) {
        return switch (plan) {
            case STARTER -> priceStarter;
            case GROWTH -> priceGrowth;
            case PRO -> pricePro;
            case TRIAL -> throw new BillingException("TRIAL nie jest planem płatnym.");
        };
    }

    private CompanyPlan planForPriceId(String priceId) {
        if (priceId != null && priceId.equals(priceStarter)) {
            return CompanyPlan.STARTER;
        }
        if (priceId != null && priceId.equals(priceGrowth)) {
            return CompanyPlan.GROWTH;
        }
        if (priceId != null && priceId.equals(pricePro)) {
            return CompanyPlan.PRO;
        }
        throw new BillingException("Nierozpoznany identyfikator ceny Stripe: " + priceId);
    }

    private SubscriptionStatus mapStatus(String stripeStatus) {
        if (stripeStatus == null) {
            return SubscriptionStatus.NONE;
        }
        return switch (stripeStatus) {
            case "active", "trialing" -> SubscriptionStatus.ACTIVE;
            case "past_due", "unpaid", "incomplete" -> SubscriptionStatus.PAST_DUE;
            default -> SubscriptionStatus.CANCELED;
        };
    }
}
