package com.aiquote.backend.lead;

import com.aiquote.backend.conversation.MessageDto;
import java.time.Instant;
import java.util.List;

public record LeadDetailResponse(
        Long id,
        String clientName,
        String clientPhone,
        String clientEmail,
        String aiSummary,
        Double estimatedPriceMin,
        Double estimatedPriceMax,
        String currency,
        String uncertainNotes,
        String status,
        Instant createdAt,
        List<MessageDto> transcript,
        List<LeadAttachmentResponse> attachments) {

    public static LeadDetailResponse from(Lead lead, List<MessageDto> transcript, List<LeadAttachmentResponse> attachments) {
        return new LeadDetailResponse(
                lead.getId(),
                lead.getClientName(),
                lead.getClientPhone(),
                lead.getClientEmail(),
                lead.getAiSummary(),
                lead.getEstimatedPriceMin(),
                lead.getEstimatedPriceMax(),
                lead.getCurrency(),
                lead.getUncertainNotes(),
                lead.getStatus().name(),
                lead.getCreatedAt(),
                transcript,
                attachments);
    }
}
