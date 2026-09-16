package com.aiquote.backend.quote;

import com.aiquote.backend.common.ApiException;
import org.springframework.http.HttpStatus;

/** Thrown when an edit or approval is attempted from a status that doesn't allow it. */
public class InvalidQuoteStatusException extends ApiException {

    public InvalidQuoteStatusException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
