package com.aiquote.backend.company;

/**
 * TRIAL is the default for every new company (see Company's constructor/migration V16
 * default) and is the only plan never billed through Stripe — a company only gets a
 * Stripe customer/subscription once it actually checks out into one of the paid plans.
 */
public enum CompanyPlan {

    TRIAL(3, 0, "Trial"),
    STARTER(15, 4900, "Starter"),
    GROWTH(50, 12900, "Growth"),
    PRO(150, 29900, "Pro");

    /** Length of the free trial period — see Company#currentPeriodEnd(). */
    public static final int TRIAL_DAYS = 7;

    /** How many draft quotes this plan allows per period (7-day trial, or calendar
     * month for a paid plan — see Company#currentPeriodStart/End). */
    private final int monthlyQuoteLimit;

    /** Price in grosz (1/100 PLN) — avoids floating point for money. 0 for TRIAL. */
    private final int priceInGrosz;

    private final String displayName;

    CompanyPlan(int monthlyQuoteLimit, int priceInGrosz, String displayName) {
        this.monthlyQuoteLimit = monthlyQuoteLimit;
        this.priceInGrosz = priceInGrosz;
        this.displayName = displayName;
    }

    public int monthlyQuoteLimit() {
        return monthlyQuoteLimit;
    }

    public int priceInGrosz() {
        return priceInGrosz;
    }

    public String displayName() {
        return displayName;
    }
}
