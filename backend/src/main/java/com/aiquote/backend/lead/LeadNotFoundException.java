package com.aiquote.backend.lead;

import com.aiquote.backend.common.ApiException;
import org.springframework.http.HttpStatus;

public class LeadNotFoundException extends ApiException {

    public LeadNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "Nie znaleziono leada: " + id);
    }
}
