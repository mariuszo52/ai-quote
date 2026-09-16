package com.aiquote.backend.company;

/** Mirrors only the Stripe subscription statuses the app actually branches on. */
public enum SubscriptionStatus {
    /** Never subscribed (still on TRIAL, or a paid plan was never actually activated). */
    NONE,
    ACTIVE,
    PAST_DUE,
    CANCELED
}
