package com.aiquote.backend.quote;

import java.time.Instant;

public record QuoteChangeLogResponse(Instant createdAt, String summary) {
}
