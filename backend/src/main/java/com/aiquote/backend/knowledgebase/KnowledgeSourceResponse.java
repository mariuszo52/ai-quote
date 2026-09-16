package com.aiquote.backend.knowledgebase;

import java.time.Instant;

public record KnowledgeSourceResponse(
        Long id,
        String type,
        String status,
        String originalFilename,
        String extractedSummary,
        Instant createdAt) {

    public static KnowledgeSourceResponse from(KnowledgeSource source, String originalFilename) {
        return new KnowledgeSourceResponse(
                source.getId(),
                source.getType().name(),
                source.getStatus().name(),
                originalFilename,
                source.getExtractedSummary(),
                source.getCreatedAt());
    }
}
