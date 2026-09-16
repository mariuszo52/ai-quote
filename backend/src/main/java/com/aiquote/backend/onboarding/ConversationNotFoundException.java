package com.aiquote.backend.onboarding;

import com.aiquote.backend.common.ApiException;
import org.springframework.http.HttpStatus;

public class ConversationNotFoundException extends ApiException {

    public ConversationNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "Nie znaleziono rozmowy: " + id);
    }
}
