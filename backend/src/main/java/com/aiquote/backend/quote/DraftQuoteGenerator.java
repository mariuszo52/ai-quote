package com.aiquote.backend.quote;

import com.aiquote.backend.ai.AiClient;
import com.aiquote.backend.ai.AiMessage;
import com.aiquote.backend.ai.AiTool;
import com.aiquote.backend.ai.AiToolUseBlock;
import com.aiquote.backend.ai.AiTurnResult;
import com.aiquote.backend.company.CompanyService;
import com.aiquote.backend.conversation.Conversation;
import com.aiquote.backend.conversation.ConversationRepository;
import com.aiquote.backend.knowledgebase.CompanyPricingProfileService;
import com.aiquote.backend.knowledgebase.PricingProfileFormatter;
import com.aiquote.backend.lead.Lead;
import com.aiquote.backend.lead.LeadRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Generates the itemized "draft quote" the owner reviews after a client submits
 * contact details — a separate bean (not a method on QuoteAgentService) so @Async
 * actually goes through the Spring proxy, same reasoning as KnowledgeSourceProcessor
 * and QuoteStreamingHandler. Triggered fire-and-forget from
 * QuoteAgentService.submitContact so the client's request stays fast; the owner simply
 * sees the quote appear in their panel a few seconds later.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DraftQuoteGenerator {

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            Jesteś asystentem AI, który na podstawie zakończonej rozmowy z klientem oraz \
            sposobu wyceny firmy "%s" przygotowuje wewnętrzną, roboczą wycenę zlecenia dla \
            właściciela firmy — NIE dla klienta. Właściciel sprawdzi i ewentualnie poprawi tę \
            wycenę, zanim cokolwiek trafi do klienta.

            Sposób wyceny firmy (ustalony przez właściciela):
            %s

            Zasady:
            - Rozbij zlecenie na pozycje (items) — każda z nazwą, opisem, ilością, jednostką \
            i ceną jednostkową.
            - NIGDY nie zgaduj ceny jednostkowej. Podawaj unitPrice TYLKO wtedy, gdy wynika \
            wprost ze sposobu wyceny firmy powyżej albo z tego, co klient jawnie podał w \
            rozmowie. Jeśli nie da się ustalić ceny danej pozycji, zostaw unitPrice puste \
            (null) — nie wymyślaj liczby.
            - Jeśli zlecenie w ogóle nie pasuje do znanego cennika firmy, i tak wypisz \
            pozycje (z unitPrice=null), ustaw niskie confidence (poniżej 0.3) i jasno opisz \
            w reasoning oraz uncertainFactors, że wymagana jest indywidualna wycena \
            właściciela.
            - confidence to liczba 0.0-1.0 opisująca Twoją pewność co do całej wyceny.
            - reasoning to krótkie, rzeczowe uzasadnienie napisane do właściciela firmy.
            - Weź pod uwagę zdjęcia przesłane przez klienta w rozmowie, jeśli są dostępne.
            - Wywołaj narzędzie create_draft_quote z wynikiem. Pisz po polsku.
            """;

    private final LeadRepository leadRepository;
    private final ConversationRepository conversationRepository;
    private final CompanyService companyService;
    private final CompanyPricingProfileService profileService;
    private final QuoteRepository quoteRepository;
    private final ConversationHistoryReader historyReader;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    @Async
    public void generate(Long leadId) {
        try {
            Lead lead = leadRepository.findById(leadId).orElse(null);
            if (lead == null) {
                log.warn("Cannot generate draft quote: lead {} not found", leadId);
                return;
            }
            Conversation conversation = conversationRepository.findById(lead.getConversationId()).orElse(null);
            if (conversation == null) {
                log.warn("Cannot generate draft quote: conversation {} not found for lead {}", lead.getConversationId(), leadId);
                return;
            }

            List<AiMessage> history = historyReader.buildHistory(conversation.getId());
            // The transcript naturally ends on the assistant's last chat reply, but
            // Anthropic requires the final message to be from the user (it would
            // otherwise be treated as an assistant-message prefill). This closing turn
            // also tells the model the conversation is over and it's time to finalize.
            history.add(AiMessage.userText(
                    "Rozmowa z klientem została zakończona. Przygotuj teraz szczegółową wycenę roboczą zgodnie z powyższymi zasadami."));
            String companyName = companyService.getById(lead.getCompanyId()).getName();
            String pricingText = PricingProfileFormatter.toPromptText(profileService.getData(lead.getCompanyId()));
            String systemPrompt = SYSTEM_PROMPT_TEMPLATE.formatted(companyName, pricingText);

            AiTool tool = DraftQuoteTool.definition("Zapisuje roboczą wycenę zlecenia dla właściciela.", objectMapper);
            AiTurnResult result = aiClient.sendMessage(systemPrompt, history, List.of(tool));

            for (AiToolUseBlock toolUse : result.extractToolUses()) {
                if (DraftQuoteTool.NAME.equals(toolUse.name())) {
                    quoteRepository.save(buildQuote(lead, conversation, toolUse.input()));
                    return;
                }
            }
            log.warn("AI did not produce a draft quote for lead {}", leadId);
        } catch (Exception e) {
            log.warn("Failed to generate draft quote for lead {}", leadId, e);
        }
    }

    /** Package-private and pure past the JsonNode input — exercised directly in tests without mocking the AI call. */
    Quote buildQuote(Lead lead, Conversation conversation, JsonNode input) {
        List<QuoteLineItem> items = parseItems(input.path("items")).stream()
                .map(QuoteCalculator::withComputedTotal)
                .toList();
        double subtotal = QuoteCalculator.subtotal(items);
        String currency = input.path("currency").asText("PLN");
        Double confidence = input.hasNonNull("confidence") ? input.path("confidence").asDouble() : null;
        String reasoning = input.path("reasoning").asText("");
        List<String> uncertainFactors = parseStringArray(input.path("uncertainFactors"));

        return new Quote(
                lead.getCompanyId(),
                conversation.getId(),
                lead.getId(),
                currency,
                writeJson(items),
                subtotal,
                subtotal,
                confidence,
                reasoning,
                writeJson(uncertainFactors));
    }

    private List<QuoteLineItem> parseItems(JsonNode itemsNode) {
        if (!itemsNode.isArray()) {
            return List.of();
        }
        List<QuoteLineItem> items = new ArrayList<>();
        for (JsonNode itemNode : itemsNode) {
            String name = itemNode.path("name").asText("");
            if (name.isBlank()) {
                continue;
            }
            items.add(new QuoteLineItem(
                    name,
                    textOrNull(itemNode, "description"),
                    numberOrNull(itemNode, "quantity"),
                    textOrNull(itemNode, "unit"),
                    numberOrNull(itemNode, "unitPrice"),
                    null,
                    textOrNull(itemNode, "source")));
        }
        return items;
    }

    private List<String> parseStringArray(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(n -> values.add(n.asText()));
        }
        return values;
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? null : value.asText();
    }

    private Double numberOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? null : value.asDouble();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize draft quote data", e);
        }
    }
}
