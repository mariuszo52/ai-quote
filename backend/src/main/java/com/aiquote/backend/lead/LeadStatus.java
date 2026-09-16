package com.aiquote.backend.lead;

/**
 * Independent from QuoteStatus (see Quote's own lifecycle) — a lead's status tracks the
 * owner's relationship with the client, not the quote document's state. QUOTE_SENT is
 * set automatically once the final offer email actually goes out (see
 * offer.OfferSendService), not manually via updateStatus — see LeadStatusTransitions.
 */
public enum LeadStatus {
    NEW,
    CONTACTED,
    QUOTE_SENT,
    WON,
    LOST
}
