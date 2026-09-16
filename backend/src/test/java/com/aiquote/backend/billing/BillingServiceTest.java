package com.aiquote.backend.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiquote.backend.auth.AppUser;
import com.aiquote.backend.auth.AppUserRepository;
import com.aiquote.backend.company.Company;
import com.aiquote.backend.company.CompanyPlan;
import com.aiquote.backend.company.CompanyRepository;
import com.aiquote.backend.company.CompanyService;
import com.aiquote.backend.company.CompanyStatus;
import com.aiquote.backend.company.SubscriptionStatus;
import com.aiquote.backend.quote.QuoteRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** Covers the SaaS billing feature's checkout/portal/webhook orchestration, all against
 * a mocked StripeGateway (real Stripe calls aren't reachable/mockable in a unit test —
 * see StripeGatewayImpl's javadoc for why it's wrapped). */
@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    private static final String FRONTEND_URL = "http://localhost:5173";
    private static final String PRICE_STARTER = "price_starter";
    private static final String PRICE_GROWTH = "price_growth";
    private static final String PRICE_PRO = "price_pro";

    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private CompanyService companyService;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private QuoteRepository quoteRepository;
    @Mock
    private StripeGateway stripeGateway;

    private BillingService service;

    @BeforeEach
    void setUp() {
        service = new BillingService(
                companyRepository, companyService, appUserRepository, quoteRepository, stripeGateway,
                FRONTEND_URL, PRICE_STARTER, PRICE_GROWTH, PRICE_PRO);
    }

    private Company company(long id) {
        Company company = new Company("Firma", "firma", CompanyStatus.ACTIVE);
        ReflectionTestUtils.setField(company, "id", id);
        ReflectionTestUtils.setField(company, "createdAt", Instant.parse("2026-01-01T00:00:00Z"));
        return company;
    }

    @Test
    void statusReportsTrialUsageAndLimit() {
        Company company = company(5L);
        when(companyService.getById(5L)).thenReturn(company);
        when(quoteRepository.countByCompanyIdAndCreatedAtBetween(eq(5L), any(), any())).thenReturn(2L);

        BillingStatusResponse status = service.getStatus(5L);

        assertThat(status.plan()).isEqualTo("TRIAL");
        assertThat(status.quotesUsed()).isEqualTo(2L);
        assertThat(status.quoteLimit()).isEqualTo(3);
        assertThat(status.trialEndsAt()).isNotNull();
        assertThat(status.canManageBilling()).isFalse();
    }

    @Test
    void statusIsNotBlockedForAFreshTrialWithQuotesToSpare() {
        Company company = new Company("Firma", "firma", CompanyStatus.ACTIVE);
        ReflectionTestUtils.setField(company, "id", 5L);
        ReflectionTestUtils.setField(company, "createdAt", Instant.now());
        when(companyService.getById(5L)).thenReturn(company);
        when(quoteRepository.countByCompanyIdAndCreatedAtBetween(eq(5L), any(), any())).thenReturn(1L);

        assertThat(service.getStatus(5L).blocked()).isFalse();
    }

    @Test
    void statusIsBlockedOnceTheSevenDayTrialWindowHasElapsed() {
        Company company = new Company("Firma", "firma", CompanyStatus.ACTIVE);
        ReflectionTestUtils.setField(company, "id", 5L);
        ReflectionTestUtils.setField(company, "createdAt", Instant.now().minus(java.time.Duration.ofDays(8)));
        when(companyService.getById(5L)).thenReturn(company);
        when(quoteRepository.countByCompanyIdAndCreatedAtBetween(eq(5L), any(), any())).thenReturn(0L);

        assertThat(service.getStatus(5L).blocked()).isTrue();
    }

    @Test
    void statusIsBlockedOnceTheQuoteAllowanceIsUsedUp() {
        Company company = new Company("Firma", "firma", CompanyStatus.ACTIVE);
        ReflectionTestUtils.setField(company, "id", 5L);
        ReflectionTestUtils.setField(company, "createdAt", Instant.now());
        when(companyService.getById(5L)).thenReturn(company);
        when(quoteRepository.countByCompanyIdAndCreatedAtBetween(eq(5L), any(), any())).thenReturn(3L);

        assertThat(service.getStatus(5L).blocked()).isTrue();
    }

    @Test
    void checkoutCreatesAStripeCustomerWhenTheCompanyDoesntHaveOneYet() {
        Company company = company(5L);
        when(companyService.getById(5L)).thenReturn(company);
        when(appUserRepository.findFirstByCompanyIdOrderByIdAsc(5L))
                .thenReturn(Optional.of(AppUser.local(5L, "owner@example.com", "hash", com.aiquote.backend.tenant.UserRole.OWNER)));
        when(stripeGateway.createCustomer("owner@example.com", "Firma")).thenReturn("cus_new");
        when(stripeGateway.createCheckoutSessionUrl(eq("cus_new"), eq(PRICE_GROWTH), any(), any()))
                .thenReturn("https://checkout.stripe.com/session123");

        RedirectUrlResponse response = service.createCheckoutSession(5L, CompanyPlan.GROWTH);

        assertThat(response.url()).isEqualTo("https://checkout.stripe.com/session123");
        assertThat(company.getStripeCustomerId()).isEqualTo("cus_new");
    }

    @Test
    void checkoutReusesAnExistingStripeCustomerWithoutCreatingAnother() {
        Company company = company(5L);
        company.applySubscription(CompanyPlan.TRIAL, "cus_existing", null, SubscriptionStatus.NONE, null, null);
        when(companyService.getById(5L)).thenReturn(company);
        when(stripeGateway.createCheckoutSessionUrl(eq("cus_existing"), eq(PRICE_STARTER), any(), any()))
                .thenReturn("https://checkout.stripe.com/session456");

        service.createCheckoutSession(5L, CompanyPlan.STARTER);

        verify(stripeGateway, never()).createCustomer(any(), any());
    }

    @Test
    void portalRequiresAnExistingStripeCustomer() {
        Company company = company(5L);
        when(companyService.getById(5L)).thenReturn(company);

        assertThatThrownBy(() -> service.createPortalSession(5L)).isInstanceOf(BillingException.class);
    }

    @Test
    void portalReturnsTheGatewayUrlWhenACustomerExists() {
        Company company = company(5L);
        company.applySubscription(CompanyPlan.STARTER, "cus_1", "sub_1", SubscriptionStatus.ACTIVE, Instant.now(), Instant.now());
        when(companyService.getById(5L)).thenReturn(company);
        when(stripeGateway.createPortalSessionUrl(eq("cus_1"), any())).thenReturn("https://billing.stripe.com/portal789");

        RedirectUrlResponse response = service.createPortalSession(5L);

        assertThat(response.url()).isEqualTo("https://billing.stripe.com/portal789");
    }

    @Test
    void webhookIgnoresEventsThatDontCarryASubscription() {
        when(stripeGateway.constructWebhookEvent(any(), any()))
                .thenReturn(new StripeWebhookEvent("some.other.event", null, null, null, null, null, null));

        service.handleWebhookEvent("payload", "sig");

        verify(companyRepository, never()).findByStripeCustomerId(any());
    }

    @Test
    void webhookIgnoresSubscriptionsForAnUnknownCustomer() {
        when(stripeGateway.constructWebhookEvent(any(), any())).thenReturn(new StripeWebhookEvent(
                "customer.subscription.updated", "cus_unknown", "sub_1", PRICE_GROWTH, "active", Instant.now(), Instant.now()));
        when(companyRepository.findByStripeCustomerId("cus_unknown")).thenReturn(Optional.empty());

        service.handleWebhookEvent("payload", "sig");
        // no exception, no crash — just a no-op, since a customer we've never heard of isn't our concern
    }

    @Test
    void webhookAppliesTheMappedPlanAndStatusToTheMatchingCompany() {
        Company company = company(5L);
        company.applySubscription(CompanyPlan.TRIAL, "cus_1", null, SubscriptionStatus.NONE, null, null);
        when(companyRepository.findByStripeCustomerId("cus_1")).thenReturn(Optional.of(company));
        Instant periodStart = Instant.parse("2026-03-01T00:00:00Z");
        Instant periodEnd = Instant.parse("2026-04-01T00:00:00Z");
        when(stripeGateway.constructWebhookEvent(any(), any())).thenReturn(new StripeWebhookEvent(
                "customer.subscription.updated", "cus_1", "sub_1", PRICE_PRO, "active", periodStart, periodEnd));

        service.handleWebhookEvent("payload", "sig");

        assertThat(company.getPlan()).isEqualTo(CompanyPlan.PRO);
        assertThat(company.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(company.currentPeriodStart()).isEqualTo(periodStart);
        assertThat(company.currentPeriodEnd()).isEqualTo(periodEnd);
    }

    @Test
    void webhookMapsCanceledStripeStatusToCanceled() {
        Company company = company(5L);
        company.applySubscription(CompanyPlan.STARTER, "cus_1", "sub_1", SubscriptionStatus.ACTIVE, Instant.now(), Instant.now());
        when(companyRepository.findByStripeCustomerId("cus_1")).thenReturn(Optional.of(company));
        when(stripeGateway.constructWebhookEvent(any(), any())).thenReturn(new StripeWebhookEvent(
                "customer.subscription.deleted", "cus_1", "sub_1", PRICE_STARTER, "canceled", Instant.now(), Instant.now()));

        service.handleWebhookEvent("payload", "sig");

        assertThat(company.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.CANCELED);
    }
}
