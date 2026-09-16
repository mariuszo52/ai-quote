package com.aiquote.backend.offer;

/**
 * READY_TO_SEND is set the moment the offer/PDF is generated at Quote approval (Etap 12).
 * SENT/SEND_FAILED are driven by the email-sending flow (Etap 13) — modeled now so that
 * stage doesn't need another migration.
 */
public enum OfferStatus {
    READY_TO_SEND,
    SENT,
    SEND_FAILED
}
