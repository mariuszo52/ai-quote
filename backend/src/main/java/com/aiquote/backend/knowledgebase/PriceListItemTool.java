package com.aiquote.backend.knowledgebase;

import com.aiquote.backend.ai.AiTool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Single source of truth for the save_price_list_items tool's input contract — shared
 * by the onboarding chat (OnboardingService, when the owner mentions a material/device in
 * conversation) and document-import extraction (KnowledgeSourceProcessor), the same way
 * PricingProfileTool is shared for services. Both feed PriceListItemService.upsertAllFromToolInput
 * with this exact shape. */
public final class PriceListItemTool {

    public static final String NAME = "save_price_list_items";

    private static final String SCHEMA_JSON = """
            {
              "type": "object",
              "properties": {
                "items": {
                  "type": "array",
                  "description": "Materiały/urządzenia, o których właściciel podał wystarczające informacje.",
                  "items": {
                    "type": "object",
                    "properties": {
                      "name": { "type": "string", "description": "Nazwa materiału/urządzenia, tak jak nazwał go właściciel." },
                      "category": { "type": ["string", "null"], "description": "Kategoria, jeśli wynika z wypowiedzi (np. rury, klimatyzacja)." },
                      "price": { "type": ["number", "null"], "description": "Cena — tylko jeśli podana wprost w wypowiedzi." },
                      "unit": { "type": ["string", "null"], "description": "Jednostka, np. sztuka, mb, komplet — tylko jeśli podana wprost." }
                    },
                    "required": ["name"]
                  }
                }
              },
              "required": ["items"]
            }
            """;

    private PriceListItemTool() {
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
