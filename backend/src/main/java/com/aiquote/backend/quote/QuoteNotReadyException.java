package com.aiquote.backend.quote;

import com.aiquote.backend.common.ApiException;
import org.springframework.http.HttpStatus;

public class QuoteNotReadyException extends ApiException {

    public QuoteNotReadyException() {
        super(HttpStatus.CONFLICT, "Ta rozmowa nie ma jeszcze przygotowanej wyceny.");
    }
}
