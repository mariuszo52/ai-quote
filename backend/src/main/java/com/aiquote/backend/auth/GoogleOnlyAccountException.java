package com.aiquote.backend.auth;

import com.aiquote.backend.common.ApiException;
import org.springframework.http.HttpStatus;

/** Thrown when a normal email/password login is attempted for an account that was
 * created via Google sign-in and has no password set. */
public class GoogleOnlyAccountException extends ApiException {

    public GoogleOnlyAccountException() {
        super(HttpStatus.BAD_REQUEST, "To konto zostało założone przez Google. Zaloguj się przez konto Google.");
    }
}
