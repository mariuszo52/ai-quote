package com.aiquote.backend.company;

import com.aiquote.backend.common.ApiException;
import org.springframework.http.HttpStatus;

public class LogoNotFoundException extends ApiException {

    public LogoNotFoundException() {
        super(HttpStatus.NOT_FOUND, "Firma nie ma jeszcze ustawionego logo.");
    }
}
