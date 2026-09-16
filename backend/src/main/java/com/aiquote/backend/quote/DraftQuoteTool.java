package com.aiquote.backend.quote;

import com.aiquote.backend.ai.AiTool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Input contract for the create_draft_quote tool the post-conversation draft-quote
 * generator forces the AI to call. Deliberately excludes subtotal/total — those are
 * always computed by QuoteCalculator from quantity * unitPrice, never trusted from the
 * model, so a quote's arithmetic can never be wrong (or fabricated) regardless of what
 * the AI returns.
 */
public final class DraftQuoteTool {

    public static final String NAME = "create_draft_quote";

    private static final String SCHEMA_JSON = """
            {
              "type": "object",
              "properties": {
                "items": {
                  "type": "array",
                  "description": "Pozycje wyceny, rozbite na konkretne elementy zlecenia.",
                  "items": {
                    "type": "object",
                    "properties": {
                      "name": { "type": "string", "description": "Nazwa pozycji." },
                      "description": { "type": ["string", "null"], "description": "Krótki opis pozycji." },
                      "quantity": { "type": ["number", "null"], "description": "Ilość, jeśli dotyczy." },
                      "unit": { "type": ["string", "null"], "description": "Jednostka, np. m2, godzina, sztuka." },
                      "unitPrice": { "type": ["number", "null"], "description": "Cena jednostkowa — TYLKO jeśli wynika wprost ze sposobu wyceny firmy lub z rozmowy z klientem. Nigdy nie zgaduj — jeśli nieznana, zostaw null." },
                      "source": { "type": ["string", "null"], "description": "Skąd wzięła się cena, np. pricing_profile albo conversation." }
                    },
                    "required": ["name"]
                  }
                },
                "currency": { "type": "string", "description": "Waluta wyceny, np. PLN." },
                "confidence": { "type": "number", "description": "0.0-1.0 — jak pewna jest ta wycena." },
                "reasoning": { "type": "string", "description": "Krótkie uzasadnienie wyceny dla właściciela firmy (nie dla klienta)." },
                "uncertainFactors": {
                  "type": "array",
                  "items": { "type": "string" },
                  "description": "Informacje, których zabrakło, albo których AI nie było pewne przy tej wycenie."
                }
              },
              "required": ["items", "currency", "confidence", "reasoning"]
            }
            """;

    private DraftQuoteTool() {
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
