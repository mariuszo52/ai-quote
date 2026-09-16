package com.aiquote.backend.quote;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiquote.backend.conversation.Conversation;
import com.aiquote.backend.conversation.ConversationType;
import com.aiquote.backend.lead.Lead;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Exercises DraftQuoteGenerator.buildQuote directly with hand-built tool-input JSON —
 * this is the logic that turns an AI response into a persisted Quote, and it's where
 * the "never invent a price" and "link to lead/conversation" guarantees actually live.
 * No AiClient/repository mocking needed since buildQuote is pure past its JsonNode input.
 */
class DraftQuoteGeneratorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DraftQuoteGenerator generator =
            new DraftQuoteGenerator(null, null, null, null, null, null, null, objectMapper);

    @Test
    void createsWaitingForOwnerQuoteLinkedToLeadAndConversation() throws Exception {
        Lead lead = lead(99L, 1L, 42L);
        Conversation conversation = conversation(42L, 1L);
        JsonNode input = toolInput("""
                {
                  "items": [{"name": "Malowanie ścian", "quantity": 120, "unit": "m2", "unitPrice": 20, "source": "pricing_profile"}],
                  "currency": "PLN",
                  "confidence": 0.87,
                  "reasoning": "Na podstawie cennika firmy.",
                  "uncertainFactors": []
                }
                """);

        Quote quote = generator.buildQuote(lead, conversation, input);

        assertThat(quote.getCompanyId()).isEqualTo(1L);
        assertThat(quote.getConversationId()).isEqualTo(42L);
        assertThat(quote.getLeadId()).isEqualTo(99L);
        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.WAITING_FOR_OWNER);
    }

    @Test
    void computesTotalFromItemsRatherThanTrustingAi() throws Exception {
        Lead lead = lead(1L, 1L, 1L);
        Conversation conversation = conversation(1L, 1L);
        JsonNode input = toolInput("""
                {
                  "items": [
                    {"name": "Malowanie ścian", "quantity": 120, "unit": "m2", "unitPrice": 20},
                    {"name": "Gruntowanie", "quantity": 120, "unit": "m2", "unitPrice": 5}
                  ],
                  "currency": "PLN",
                  "confidence": 0.9,
                  "reasoning": "Test"
                }
                """);

        Quote quote = generator.buildQuote(lead, conversation, input);

        assertThat(quote.getSubtotal()).isEqualTo(3000.0);
        assertThat(quote.getTotal()).isEqualTo(3000.0);
        assertThat(items(quote)).extracting(QuoteLineItem::totalPrice).containsExactly(2400.0, 600.0);
    }

    @Test
    void leavesPriceUnsetInsteadOfInventingOneWhenAiDoesNotKnowIt() throws Exception {
        Lead lead = lead(1L, 1L, 1L);
        Conversation conversation = conversation(1L, 1L);
        JsonNode input = toolInput("""
                {
                  "items": [{"name": "Nietypowa naprawa", "description": "Nie pasuje do znanego cennika", "unitPrice": null}],
                  "currency": "PLN",
                  "confidence": 0.15,
                  "reasoning": "Zlecenie nie pasuje do znanego cennika firmy — wymaga indywidualnej wyceny.",
                  "uncertainFactors": ["Brak cennika dla tego typu naprawy"]
                }
                """);

        Quote quote = generator.buildQuote(lead, conversation, input);

        assertThat(quote.getSubtotal()).isZero();
        assertThat(quote.getTotal()).isZero();
        assertThat(quote.getAiConfidence()).isEqualTo(0.15);
        assertThat(items(quote)).hasSize(1);
        assertThat(items(quote).get(0).unitPrice()).isNull();
        assertThat(items(quote).get(0).totalPrice()).isNull();
        assertThat(uncertainFactors(quote)).containsExactly("Brak cennika dla tego typu naprawy");
    }

    @Test
    void insufficientPricingDataStillProducesAQuoteInsteadOfFailing() throws Exception {
        Lead lead = lead(1L, 1L, 1L);
        Conversation conversation = conversation(1L, 1L);
        // Company has no pricing profile at all — AI can still describe the job, just with no prices.
        JsonNode input = toolInput("""
                {
                  "items": [],
                  "currency": "PLN",
                  "confidence": 0.1,
                  "reasoning": "Brak wystarczających danych cenowych firmy, aby przygotować wycenę.",
                  "uncertainFactors": ["Firma nie uzupełniła jeszcze cennika"]
                }
                """);

        Quote quote = generator.buildQuote(lead, conversation, input);

        assertThat(items(quote)).isEmpty();
        assertThat(quote.getSubtotal()).isZero();
        assertThat(quote.getTotal()).isZero();
        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.WAITING_FOR_OWNER);
    }

    @Test
    void skipsItemsWithBlankName() throws Exception {
        Lead lead = lead(1L, 1L, 1L);
        Conversation conversation = conversation(1L, 1L);
        JsonNode input = toolInput("""
                {
                  "items": [
                    {"name": "", "unitPrice": 10},
                    {"name": "Prawdziwa pozycja", "quantity": 1, "unitPrice": 10}
                  ],
                  "currency": "PLN",
                  "confidence": 0.5,
                  "reasoning": "Test"
                }
                """);

        Quote quote = generator.buildQuote(lead, conversation, input);

        assertThat(items(quote)).extracting(QuoteLineItem::name).containsExactly("Prawdziwa pozycja");
    }

    private JsonNode toolInput(String json) throws Exception {
        return objectMapper.readTree(json);
    }

    private List<QuoteLineItem> items(Quote quote) throws Exception {
        return objectMapper.readValue(quote.getItemsJson(), new TypeReference<List<QuoteLineItem>>() {
        });
    }

    private List<String> uncertainFactors(Quote quote) throws Exception {
        return objectMapper.readValue(quote.getUncertainFactorsJson(), new TypeReference<List<String>>() {
        });
    }

    private Lead lead(long id, long companyId, long conversationId) {
        Lead lead = new Lead(companyId, conversationId, "Jan Kowalski", "600111222", null, null, null, null, null, null);
        ReflectionTestUtils.setField(lead, "id", id);
        return lead;
    }

    private Conversation conversation(long id, long companyId) {
        Conversation conversation = new Conversation(companyId, ConversationType.CLIENT_QUOTE);
        ReflectionTestUtils.setField(conversation, "id", id);
        return conversation;
    }
}
