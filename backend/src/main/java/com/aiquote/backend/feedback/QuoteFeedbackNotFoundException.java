package com.aiquote.backend.feedback;

import com.aiquote.backend.common.ApiException;
import org.springframework.http.HttpStatus;

public class QuoteFeedbackNotFoundException extends ApiException {

    public QuoteFeedbackNotFoundException(Long quoteId) {
        super(HttpStatus.NOT_FOUND, "Nie znaleziono danych porównawczych dla wyceny: " + quoteId);
    }
}
