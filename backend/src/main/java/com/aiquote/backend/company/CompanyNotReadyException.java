package com.aiquote.backend.company;

import com.aiquote.backend.common.ApiException;
import org.springframework.http.HttpStatus;

public class CompanyNotReadyException extends ApiException {

    public CompanyNotReadyException() {
        super(HttpStatus.CONFLICT, "Ta firma nie ukończyła jeszcze konfiguracji.");
    }
}
