package com.aiquote.backend.common;

import java.time.Instant;
import java.util.List;

public record ApiError(String message, Instant timestamp, List<String> details) {

    public static ApiError of(String message) {
        return new ApiError(message, Instant.now(), List.of());
    }

    public static ApiError of(String message, List<String> details) {
        return new ApiError(message, Instant.now(), details);
    }
}
