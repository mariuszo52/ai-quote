package com.aiquote.backend.company;

import com.aiquote.backend.common.ApiException;
import org.springframework.http.HttpStatus;

public class CompanyNotFoundException extends ApiException {

    public CompanyNotFoundException(String slug) {
        super(HttpStatus.NOT_FOUND, "Nie znaleziono firmy: " + slug);
    }
}
