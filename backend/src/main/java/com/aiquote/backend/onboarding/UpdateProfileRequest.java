package com.aiquote.backend.onboarding;

import com.aiquote.backend.knowledgebase.PricingServiceEntry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UpdateProfileRequest(
        @NotNull(message = "Lista usług jest wymagana") @Valid List<PricingServiceEntry> services,
        String generalNotes) {
}
