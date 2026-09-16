package com.aiquote.backend.company;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Covers the trial/subscription period math added for the SaaS billing feature. */
class CompanyTest {

    @Test
    void newCompanyDefaultsToTrialWithNoStripeState() {
        Company company = new Company("Firma", "firma", CompanyStatus.ACTIVE);

        assertThat(company.isOnTrial()).isTrue();
        assertThat(company.getPlan()).isEqualTo(CompanyPlan.TRIAL);
        assertThat(company.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.NONE);
        assertThat(company.quoteLimit()).isEqualTo(3);
    }

    @Test
    void trialPeriodIsSevenDaysFromSignupAndNeverStoredInTheDb() {
        Company company = new Company("Firma", "firma", CompanyStatus.ACTIVE);
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        ReflectionTestUtils.setField(company, "createdAt", createdAt);

        assertThat(company.currentPeriodStart()).isEqualTo(createdAt);
        assertThat(company.currentPeriodEnd()).isEqualTo(createdAt.plus(Duration.ofDays(7)));
    }

    @Test
    void paidPlanUsesTheStripeReportedPeriodInstead() {
        Company company = new Company("Firma", "firma", CompanyStatus.ACTIVE);
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        ReflectionTestUtils.setField(company, "createdAt", createdAt);
        Instant stripeStart = Instant.parse("2026-03-01T00:00:00Z");
        Instant stripeEnd = Instant.parse("2026-04-01T00:00:00Z");

        company.applySubscription(CompanyPlan.GROWTH, "cus_1", "sub_1", SubscriptionStatus.ACTIVE, stripeStart, stripeEnd);

        assertThat(company.isOnTrial()).isFalse();
        assertThat(company.currentPeriodStart()).isEqualTo(stripeStart);
        assertThat(company.currentPeriodEnd()).isEqualTo(stripeEnd);
        assertThat(company.quoteLimit()).isEqualTo(50);
        assertThat(company.getStripeCustomerId()).isEqualTo("cus_1");
        assertThat(company.getStripeSubscriptionId()).isEqualTo("sub_1");
        assertThat(company.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void trialIsNotExpiredWithinTheSevenDayWindow() {
        Company company = new Company("Firma", "firma", CompanyStatus.ACTIVE);
        ReflectionTestUtils.setField(company, "createdAt", Instant.now().minus(Duration.ofDays(3)));

        assertThat(company.isTrialExpired()).isFalse();
    }

    @Test
    void trialExpiresOnceSevenDaysHavePassedRegardlessOfQuotesUsed() {
        Company company = new Company("Firma", "firma", CompanyStatus.ACTIVE);
        ReflectionTestUtils.setField(company, "createdAt", Instant.now().minus(Duration.ofDays(8)));

        assertThat(company.isTrialExpired()).isTrue();
    }

    @Test
    void aPaidPlanIsNeverConsideredTrialExpiredEvenIfItsBillingPeriodHasLapsed() {
        Company company = new Company("Firma", "firma", CompanyStatus.ACTIVE);
        ReflectionTestUtils.setField(company, "createdAt", Instant.now().minus(Duration.ofDays(30)));
        Instant longAgo = Instant.now().minus(Duration.ofDays(10));
        company.applySubscription(CompanyPlan.STARTER, "cus_1", "sub_1", SubscriptionStatus.ACTIVE, longAgo, longAgo);

        assertThat(company.isTrialExpired()).isFalse();
    }

    @Test
    void eachPlanHasItsOwnMonthlyQuoteLimit() {
        assertThat(CompanyPlan.TRIAL.monthlyQuoteLimit()).isEqualTo(3);
        assertThat(CompanyPlan.STARTER.monthlyQuoteLimit()).isEqualTo(15);
        assertThat(CompanyPlan.GROWTH.monthlyQuoteLimit()).isEqualTo(50);
        assertThat(CompanyPlan.PRO.monthlyQuoteLimit()).isEqualTo(150);
    }
}
