package com.aiquote.backend.knowledgebase;

import com.aiquote.backend.ai.AiTool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Single source of truth for the update_pricing_profile tool's input contract, shared
 * by the onboarding chat (OnboardingService) and document-import extraction
 * (KnowledgeSourceProcessor) — both feed CompanyPricingProfileService.mergeAiUpdate
 * with the same shape, so they must agree on exactly what that shape is.
 */
public final class PricingProfileTool {

    public static final String NAME = "update_pricing_profile";

    private static final String SCHEMA_JSON = """
            {
              "type": "object",
              "properties": {
                "services": {
                  "type": "array",
                  "description": "Usługi, o których dowiedziałeś się nowych informacji cenowych.",
                  "items": {
                    "type": "object",
                    "properties": {
                      "name": { "type": "string", "description": "Nazwa usługi, tak jak nazywa ją właściciel." },
                      "pricingModel": { "type": "string", "enum": ["HOURLY", "PER_UNIT", "PER_PROJECT", "INDIVIDUAL"], "description": "Sposób naliczania ceny." },
                      "unit": { "type": ["string", "null"], "description": "Jednostka rozliczeniowa, np. godzina, m2, sztuka." },
                      "basePrice": { "type": ["number", "null"], "description": "Typowa/bazowa cena — tylko jeśli podana wprost." },
                      "minPrice": { "type": ["number", "null"], "description": "Dolna granica widełek cenowych — tylko jeśli podana wprost." },
                      "maxPrice": { "type": ["number", "null"], "description": "Górna granica widełek cenowych — tylko jeśli podana wprost." },
                      "minimumCharge": { "type": ["number", "null"], "description": "Minimalna opłata za zlecenie, jeśli istnieje." },
                      "factors": { "type": "array", "items": { "type": "string" }, "description": "Czynniki wpływające na cenę tej usługi." },
                      "requiresIndividualQuote": { "type": "boolean", "description": "True, jeśli ta usługa zwykle wymaga indywidualnej wyceny." },
                      "notes": { "type": ["string", "null"], "description": "Dodatkowe uwagi dotyczące tylko tej usługi." }
                    },
                    "required": ["name"]
                  }
                },
                "generalNotes": {
                  "type": ["string", "null"],
                  "description": "Ogólne uwagi niezwiązane z jedną usługą (np. zasady dojazdu, płatności) — tylko jeśli się zmieniły."
                }
              },
              "required": ["services"]
            }
            """;

    private PricingProfileTool() {
    }

    public static AiTool definition(String description, ObjectMapper objectMapper) {
        return new AiTool(NAME, description, readSchema(objectMapper));
    }

    private static JsonNode readSchema(ObjectMapper objectMapper) {
        try {
            return objectMapper.readTree(SCHEMA_JSON);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid tool schema", e);
        }
    }
}
