package com.aiquote.backend.company;

import com.aiquote.backend.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "companies")
@Getter
@NoArgsConstructor
public class Company extends BaseEntity {

    /** App-wide default accent color (matches the AI Quote frontend's own
     * --color-primary) — used whenever a company hasn't set its own (Etap 17 #3). */
    public static final String DEFAULT_PRIMARY_COLOR = "#4f46e5";

    public static final String DEFAULT_WELCOME_TEXT = "Witaj! Odpowiedz na kilka pytań, a przygotujemy dla Ciebie wycenę.";

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CompanyStatus status;

    /** Optional public-facing override of {@link #name} (e.g. a trading name that
     * differs from the legal one) — falls back to name when unset, see getDisplayName(). */
    @Column(name = "display_name")
    private String displayName;

    @Column(name = "logo_storage_key")
    private String logoStorageKey;

    @Column(name = "logo_content_type")
    private String logoContentType;

    @Column(name = "primary_color", length = 7)
    private String primaryColor;

    @Column(name = "welcome_text", columnDefinition = "text")
    private String welcomeText;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(columnDefinition = "text")
    private String address;

    /** TRIAL for every new company — see CompanyPlan's javadoc. Never null: Hibernate
     * sends this on every INSERT, so the DB column default alone isn't enough. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CompanyPlan plan = CompanyPlan.TRIAL;

    @Column(name = "stripe_customer_id")
    private String stripeCustomerId;

    @Column(name = "stripe_subscription_id")
    private String stripeSubscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_status", nullable = false, length = 32)
    private SubscriptionStatus subscriptionStatus = SubscriptionStatus.NONE;

    @Column(name = "current_period_start")
    private Instant storedCurrentPeriodStart;

    @Column(name = "current_period_end")
    private Instant storedCurrentPeriodEnd;

    public Company(String name, String slug, CompanyStatus status) {
        this.name = name;
        this.slug = slug;
        this.status = status;
    }

    public void activate() {
        this.status = CompanyStatus.ACTIVE;
    }

    public boolean isOnTrial() {
        return plan == CompanyPlan.TRIAL;
    }

    /** For TRIAL, the period is always the 7 days from signup (never stored — there's
     * nothing to update). For a paid plan, it's the billing period Stripe reports via
     * the webhook (see applySubscription). */
    public Instant currentPeriodStart() {
        return isOnTrial() ? getCreatedAt() : storedCurrentPeriodStart;
    }

    public Instant currentPeriodEnd() {
        return isOnTrial() ? getCreatedAt().plus(Duration.ofDays(CompanyPlan.TRIAL_DAYS)) : storedCurrentPeriodEnd;
    }

    public int quoteLimit() {
        return plan.monthlyQuoteLimit();
    }

    /** True once the 7-day trial window has elapsed, regardless of how many of the 3
     * trial quotes were actually used — the trial ends at 7 days OR 3 quotes,
     * whichever comes first, and quoteLimit() alone only ever enforces the latter
     * since currentPeriodEnd() for TRIAL never moves forward on its own. */
    public boolean isTrialExpired() {
        return isOnTrial() && Instant.now().isAfter(currentPeriodEnd());
    }

    /** The one mutator the billing webhook handler calls (Etap: SaaS billing) —
     * applies whatever Stripe reports as the subscription's current state. */
    public void applySubscription(
            CompanyPlan plan,
            String stripeCustomerId,
            String stripeSubscriptionId,
            SubscriptionStatus subscriptionStatus,
            Instant periodStart,
            Instant periodEnd) {
        this.plan = plan;
        this.stripeCustomerId = stripeCustomerId;
        this.stripeSubscriptionId = stripeSubscriptionId;
        this.subscriptionStatus = subscriptionStatus;
        this.storedCurrentPeriodStart = periodStart;
        this.storedCurrentPeriodEnd = periodEnd;
    }

    public String getDisplayName() {
        return (displayName != null && !displayName.isBlank()) ? displayName : name;
    }

    public String getResolvedPrimaryColor() {
        return (primaryColor != null && !primaryColor.isBlank()) ? primaryColor : DEFAULT_PRIMARY_COLOR;
    }

    public String getResolvedWelcomeText() {
        return (welcomeText != null && !welcomeText.isBlank()) ? welcomeText : DEFAULT_WELCOME_TEXT;
    }

    public boolean hasLogo() {
        return logoStorageKey != null;
    }

    public void updateBranding(
            String displayName, String primaryColor, String welcomeText, String contactEmail, String contactPhone, String address) {
        this.displayName = blankToNull(displayName);
        this.primaryColor = blankToNull(primaryColor);
        this.welcomeText = blankToNull(welcomeText);
        this.contactEmail = blankToNull(contactEmail);
        this.contactPhone = blankToNull(contactPhone);
        this.address = blankToNull(address);
    }

    public void attachLogo(String storageKey, String contentType) {
        this.logoStorageKey = storageKey;
        this.logoContentType = contentType;
    }

    public void removeLogo() {
        this.logoStorageKey = null;
        this.logoContentType = null;
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
