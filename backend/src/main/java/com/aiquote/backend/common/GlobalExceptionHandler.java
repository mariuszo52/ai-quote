package com.aiquote.backend.common;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex) {
        return ResponseEntity.status(ex.getStatus()).body(ApiError.of(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError.of("Validation failed", details));
    }

    /**
     * Without this, an uncaught exception (e.g. the AI provider call failing) forwards to
     * Spring's /error, which isn't permitAll and isn't re-authenticated on the forward —
     * the client sees a bare 403 that looks identical to a CORS/auth misconfiguration.
     *
     * Etap 21: an uncaught exception's raw message is never returned to the client — it
     * can (and, during testing, did — a PDFBox glyph error, a raw Postgres constraint
     * violation) contain internal details like class/field names, SQL, table/constraint
     * names, or storage paths. The full exception is logged server-side instead, where
     * it's actually useful for debugging, and the client gets a generic message.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiError.of("Wystąpił nieoczekiwany błąd. Spróbuj ponownie później."));
    }
}
