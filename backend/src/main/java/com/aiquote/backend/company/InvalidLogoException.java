package com.aiquote.backend.company;

import com.aiquote.backend.common.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidLogoException extends ApiException {

    public InvalidLogoException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
