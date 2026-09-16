package com.aiquote.backend.email;

/** Deliberately a plain RuntimeException, not an ApiException — callers (OfferService)
 * catch this and decide what the caller-facing error/state should be, since a failed
 * send has different consequences (record SEND_FAILED, don't lose the offer) than a
 * typical request error. */
public class EmailSendException extends RuntimeException {

    public EmailSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
