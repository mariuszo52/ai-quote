package com.aiquote.backend.onboarding;

import com.aiquote.backend.knowledgebase.PricingServiceEntry;
import java.time.Instant;
import java.util.List;

public record ProfileResponse(List<PricingServiceEntry> services, String generalNotes, Instant updatedAt, Instant manuallyEditedAt) {
}
