package com.aiquote.backend.conversation;

import java.time.Instant;

public record MessageDto(String role, String content, Instant createdAt) {
}
