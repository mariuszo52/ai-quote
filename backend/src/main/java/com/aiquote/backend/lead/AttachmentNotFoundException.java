package com.aiquote.backend.lead;

import com.aiquote.backend.common.ApiException;
import org.springframework.http.HttpStatus;

public class AttachmentNotFoundException extends ApiException {

    public AttachmentNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "Nie znaleziono załącznika: " + id);
    }
}
