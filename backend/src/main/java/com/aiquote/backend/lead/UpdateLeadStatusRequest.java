package com.aiquote.backend.lead;

import jakarta.validation.constraints.NotNull;

public record UpdateLeadStatusRequest(@NotNull(message = "Status jest wymagany") LeadStatus status) {
}
