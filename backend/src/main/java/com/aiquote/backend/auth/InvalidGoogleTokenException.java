package com.aiquote.backend.auth;

import com.aiquote.backend.common.ApiException;
import org.springframework.http.HttpStatus;

/** Thrown when a Google ID token fails signature/audience/expiry verification. */
public class InvalidGoogleTokenException extends ApiException {

    public InvalidGoogleTokenException() {
        super(HttpStatus.BAD_REQUEST, "Nie udało się zweryfikować logowania Google. Spróbuj ponownie.");
    }
}
