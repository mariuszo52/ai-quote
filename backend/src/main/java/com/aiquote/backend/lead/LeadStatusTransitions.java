package com.aiquote.backend.lead;

import java.util.Map;
import java.util.Set;

/**
 * Governs which LeadStatus changes an owner may make through the PATCH /api/leads/{id}
 * endpoint (see LeadService#updateStatus) — the frontend must never be able to set an
 * arbitrary status. QUOTE_SENT is deliberately unreachable here: it's a system fact set
 * only by Lead#markQuoteSent when the offer email actually sends (see
 * offer.OfferSendService), never a manual owner action.
 */
public final class LeadStatusTransitions {

    private static final Map<LeadStatus, Set<LeadStatus>> MANUALLY_ALLOWED = Map.of(
            LeadStatus.NEW, Set.of(LeadStatus.CONTACTED, LeadStatus.LOST),
            LeadStatus.CONTACTED, Set.of(LeadStatus.WON, LeadStatus.LOST),
            LeadStatus.QUOTE_SENT, Set.of(LeadStatus.WON, LeadStatus.LOST),
            LeadStatus.WON, Set.of(),
            LeadStatus.LOST, Set.of());

    private LeadStatusTransitions() {
    }

    public static boolean isManuallyAllowed(LeadStatus from, LeadStatus to) {
        return MANUALLY_ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }
}
