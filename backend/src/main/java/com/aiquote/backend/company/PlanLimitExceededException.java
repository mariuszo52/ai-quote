package com.aiquote.backend.company;

import com.aiquote.backend.common.ApiException;
import org.springframework.http.HttpStatus;

public class PlanLimitExceededException extends ApiException {

    public PlanLimitExceededException() {
        super(HttpStatus.CONFLICT, "Ta firma tymczasowo nie przyjmuje nowych zapytań. Spróbuj ponownie później.");
    }
}
