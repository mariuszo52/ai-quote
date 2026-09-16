package com.aiquote.backend.knowledgebase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompanyPricingProfileService {

    private final CompanyPricingProfileRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public CompanyPricingProfile getOrCreate(Long companyId) {
        return repository.findByCompanyId(companyId)
                .orElseGet(() -> repository.save(new CompanyPricingProfile(companyId)));
    }

    @Transactional
    public PricingProfileData getData(Long companyId) {
        return parse(getOrCreate(companyId).getProfileJson());
    }

    /**
     * The owner reviewed a form and hit save — that's a deliberate, complete statement
     * of the truth, so it fully replaces the stored profile (unlike mergeAiUpdate).
     */
    @Transactional
    public CompanyPricingProfile replaceManually(Long companyId, PricingProfileData replacement) {
        CompanyPricingProfile profile = getOrCreate(companyId);
        profile.updateManually(toJson(replacement));
        return profile;
    }

    /**
     * AI-sourced updates (onboarding chat, document import) never fully replace the
     * profile — they merge field-by-field via PricingProfileMerger, so an AI turn that
     * doesn't restate a value can't make it disappear.
     */
    @Transactional
    public CompanyPricingProfile mergeAiUpdate(Long companyId, PricingProfileData incoming) {
        CompanyPricingProfile profile = getOrCreate(companyId);
        PricingProfileData merged = PricingProfileMerger.merge(parse(profile.getProfileJson()), incoming);
        profile.updateFromAi(toJson(merged));
        return profile;
    }

    /**
     * Handles both the current structured shape and the pre-Etap-8 {"summary": "..."}
     * shape, so profiles created before this change don't just silently lose their data.
     */
    private PricingProfileData parse(String json) {
        if (json == null || json.isBlank()) {
            return PricingProfileData.empty();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.has("services")) {
                return objectMapper.treeToValue(node, PricingProfileData.class);
            }
            if (node.has("summary")) {
                String legacySummary = node.path("summary").asText(null);
                return new PricingProfileData(List.of(), (legacySummary == null || legacySummary.isBlank()) ? null : legacySummary);
            }
            return PricingProfileData.empty();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse pricing profile JSON", e);
        }
    }

    private String toJson(PricingProfileData data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize pricing profile", e);
        }
    }
}
