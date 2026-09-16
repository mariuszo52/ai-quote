package com.aiquote.backend.quote;

/**
 * SENT_TO_CLIENT/SEND_FAILED mirror the Offer's own send status (see offer.OfferStatus)
 * — set via Quote#markSentToClient/markSendFailed, called from
 * offer.OfferSendService once an actual send attempt succeeds or fails. Quote and Lead
 * statuses are otherwise independent (see lead.LeadStatus); this mirroring exists only
 * because the quote IS the document being sent, so its own lifecycle should reflect
 * that fact for anyone looking at just the quote list.
 */
public enum QuoteStatus {
    DRAFT,
    WAITING_FOR_OWNER,
    OWNER_EDITED,
    APPROVED,
    SENT_TO_CLIENT,
    SEND_FAILED,
    CANCELLED
}
