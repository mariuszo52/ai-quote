package com.aiquote.backend.lead;

public record LeadStatusResponse(Long id, String status) {

    public static LeadStatusResponse from(Lead lead) {
        return new LeadStatusResponse(lead.getId(), lead.getStatus().name());
    }
}
