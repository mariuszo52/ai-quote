package com.aiquote.backend.quote;

import com.aiquote.backend.common.ApiException;
import org.springframework.http.HttpStatus;

public class QuoteNotFoundException extends ApiException {

    public QuoteNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "Nie znaleziono wyceny: " + id);
    }
}
