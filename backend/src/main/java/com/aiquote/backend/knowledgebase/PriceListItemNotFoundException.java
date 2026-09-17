package com.aiquote.backend.knowledgebase;

import com.aiquote.backend.common.ApiException;
import org.springframework.http.HttpStatus;

public class PriceListItemNotFoundException extends ApiException {

    public PriceListItemNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "Nie znaleziono materiału o id " + id + ".");
    }
}
