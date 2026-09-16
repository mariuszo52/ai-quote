package com.aiquote.backend.offer;

import com.aiquote.backend.common.ApiException;
import org.springframework.http.HttpStatus;

public class OfferNotFoundException extends ApiException {

    public OfferNotFoundException(Long quoteId) {
        super(HttpStatus.NOT_FOUND, "Nie znaleziono oferty dla wyceny: " + quoteId);
    }

    public OfferNotFoundException(String publicToken) {
        super(HttpStatus.NOT_FOUND, "Nie znaleziono oferty.");
    }
}
