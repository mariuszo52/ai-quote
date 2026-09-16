package com.aiquote.backend.offer;

import com.aiquote.backend.common.ApiException;
import org.springframework.http.HttpStatus;

/** Thrown when trying to send an offer whose client has no email on file — sending must
 * be blocked, not silently skipped, and the offer must not be marked as sent. */
public class MissingClientEmailException extends ApiException {

    public MissingClientEmailException() {
        super(HttpStatus.CONFLICT, "Klient nie podał adresu e-mail — nie można wysłać oferty. Skontaktuj się z klientem telefonicznie.");
    }
}
