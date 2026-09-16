package com.aiquote.backend.lead;

import com.aiquote.backend.common.ApiException;
import org.springframework.http.HttpStatus;

/** Thrown when a requested LeadStatus change isn't a manually-allowed transition — see LeadStatusTransitions. */
public class InvalidLeadStatusException extends ApiException {

    public InvalidLeadStatusException(LeadStatus from, LeadStatus to) {
        super(HttpStatus.CONFLICT, "Nie można zmienić statusu leada z " + from + " na " + to + ".");
    }
}
