package com.aiquote.backend.quote;

import com.aiquote.backend.common.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidAttachmentException extends ApiException {

    public InvalidAttachmentException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
