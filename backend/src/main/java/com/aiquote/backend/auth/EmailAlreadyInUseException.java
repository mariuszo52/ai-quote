package com.aiquote.backend.auth;

import com.aiquote.backend.common.ApiException;
import org.springframework.http.HttpStatus;

public class EmailAlreadyInUseException extends ApiException {

    public EmailAlreadyInUseException(String email) {
        super(HttpStatus.CONFLICT, "Email already in use: " + email);
    }
}
