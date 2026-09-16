package com.aiquote.backend.lead;

import com.aiquote.backend.conversation.Attachment;
import com.aiquote.backend.conversation.AttachmentRepository;
import com.aiquote.backend.conversation.MessageDto;
import com.aiquote.backend.conversation.MessageRepository;
import com.aiquote.backend.file.StorageService;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deliberately takes plain contact fields rather than the public quote package's
 * request DTO — quote already depends on lead (to create leads from a conversation),
 * so lead must not depend back on quote's types or the package graph would cycle.
 */
@Service
@RequiredArgsConstructor
public class LeadService {

    private final LeadRepository leadRepository;
    private final MessageRepository messageRepository;
    private final AttachmentRepository attachmentRepository;
    private final StorageService storageService;

    @Transactional
    public Lead createFromConversation(
            Long companyId,
            Long conversationId,
            JsonNode quoteJson,
            String clientName,
            String clientPhone,
            String clientEmail) {
        String reasoning = quoteJson.path("reasoning").asText("");
        double min = quoteJson.path("min_price").asDouble();
        double max = quoteJson.path("max_price").asDouble();
        String currency = quoteJson.path("currency").asText("PLN");

        List<String> uncertainFactors = new ArrayList<>();
        JsonNode factorsNode = quoteJson.path("uncertain_factors");
        if (factorsNode.isArray()) {
            factorsNode.forEach(node -> uncertainFactors.add(node.asText()));
        }
        String uncertainNotes = String.join("; ", uncertainFactors);

        Lead lead = new Lead(
                companyId,
                conversationId,
                clientName,
                clientPhone,
                clientEmail,
                reasoning,
                min,
                max,
                currency,
                uncertainNotes.isBlank() ? null : uncertainNotes);

        return leadRepository.save(lead);
    }

    /** Etap 20: recovery path for a raced double-submit that lost the unique-constraint
     * race in createFromConversation — the winning request's row is guaranteed to exist. */
    public Lead getByConversationId(Long conversationId) {
        return leadRepository.findByConversationId(conversationId)
                .orElseThrow(() -> new IllegalStateException("Expected a lead for conversation " + conversationId + " after a unique-constraint conflict"));
    }

    public LeadDetailResponse getDetail(Long companyId, Long leadId) {
        Lead lead = leadRepository.findByIdAndCompanyId(leadId, companyId)
                .orElseThrow(() -> new LeadNotFoundException(leadId));

        List<MessageDto> transcript = messageRepository.findByConversationIdOrderByIdAsc(lead.getConversationId()).stream()
                .map(message -> new MessageDto(message.getRole().name(), message.getContent(), message.getCreatedAt()))
                .toList();

        List<LeadAttachmentResponse> attachments = attachmentRepository.findByConversationIdOrderByIdAsc(lead.getConversationId()).stream()
                .map(LeadAttachmentResponse::from)
                .toList();

        return LeadDetailResponse.from(lead, transcript, attachments);
    }

    public LeadAttachmentContent getAttachmentContent(Long companyId, Long leadId, Long attachmentId) {
        Lead lead = leadRepository.findByIdAndCompanyId(leadId, companyId)
                .orElseThrow(() -> new LeadNotFoundException(leadId));

        Attachment attachment = attachmentRepository.findById(attachmentId)
                .filter(candidate -> candidate.getConversationId() != null
                        && candidate.getConversationId().equals(lead.getConversationId()))
                .orElseThrow(() -> new AttachmentNotFoundException(attachmentId));

        try (InputStream in = storageService.retrieve(attachment.getStorageKey())) {
            byte[] bytes = in.readAllBytes();
            String contentType = attachment.getMimeType() != null ? attachment.getMimeType() : "application/octet-stream";
            return new LeadAttachmentContent(bytes, contentType, attachment.getOriginalFilename());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read attachment: " + attachment.getStorageKey(), e);
        }
    }

    @Transactional
    public LeadStatusResponse updateStatus(Long companyId, Long leadId, LeadStatus status) {
        Lead lead = leadRepository.findByIdAndCompanyId(leadId, companyId)
                .orElseThrow(() -> new LeadNotFoundException(leadId));
        if (!LeadStatusTransitions.isManuallyAllowed(lead.getStatus(), status)) {
            throw new InvalidLeadStatusException(lead.getStatus(), status);
        }
        lead.updateStatus(status);
        return LeadStatusResponse.from(lead);
    }
}
