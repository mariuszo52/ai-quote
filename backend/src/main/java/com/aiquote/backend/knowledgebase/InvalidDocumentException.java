package com.aiquote.backend.knowledgebase;

import com.aiquote.backend.common.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidDocumentException extends ApiException {

    public InvalidDocumentException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
