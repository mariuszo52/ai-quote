package com.aiquote.backend.quote;

import com.aiquote.backend.ai.AiClient;
import com.aiquote.backend.ai.AiContentBlock;
import com.aiquote.backend.ai.AiMessage;
import com.aiquote.backend.ai.AiRole;
import com.aiquote.backend.ai.AiTool;
import com.aiquote.backend.ai.AiToolResultBlock;
import com.aiquote.backend.ai.AiToolUseBlock;
import com.aiquote.backend.ai.AiTurnResult;
import com.aiquote.backend.company.Company;
import com.aiquote.backend.company.CompanyNotReadyException;
import com.aiquote.backend.company.CompanyService;
import com.aiquote.backend.company.CompanyStatus;
import com.aiquote.backend.company.LogoContent;
import com.aiquote.backend.company.PlanLimitExceededException;
import com.aiquote.backend.conversation.Attachment;
import com.aiquote.backend.conversation.AttachmentKind;
import com.aiquote.backend.conversation.AttachmentRepository;
import com.aiquote.backend.conversation.Conversation;
import com.aiquote.backend.conversation.ConversationRepository;
import com.aiquote.backend.conversation.ConversationStatus;
import com.aiquote.backend.conversation.ConversationType;
import com.aiquote.backend.conversation.Message;
import com.aiquote.backend.conversation.MessageRepository;
import com.aiquote.backend.conversation.MessageRole;
import com.aiquote.backend.file.FileSignature;
import com.aiquote.backend.file.StorageService;
import com.aiquote.backend.knowledgebase.CompanyPricingProfileService;
import com.aiquote.backend.knowledgebase.PriceListFormatter;
import com.aiquote.backend.knowledgebase.PriceListItem;
import com.aiquote.backend.knowledgebase.PriceListItemRepository;
import com.aiquote.backend.knowledgebase.PricingProfileData;
import com.aiquote.backend.knowledgebase.PricingProfileFormatter;
import com.aiquote.backend.lead.Lead;
import com.aiquote.backend.lead.LeadService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Client-facing counterpart to OnboardingService: same tool-use loop shape, but the
 * tool is propose_quote instead of update_pricing_profile, there is no session resume
 * (every /q/{slug} visit is a fresh anonymous conversation), and access is guarded by
 * a random per-conversation token instead of a JWT/TenantContext.
 */
@Service
@RequiredArgsConstructor
public class QuoteAgentService {

    private static final int MAX_TOOL_LOOP_ITERATIONS = 4;
    private static final String PROPOSE_QUOTE_TOOL_NAME = "propose_quote";
    private static final String SUGGEST_OPTIONS_TOOL_NAME = "suggest_options";
    private static final long MAX_IMAGE_SIZE_BYTES = 8L * 1024 * 1024;
    private static final Set<String> ALLOWED_IMAGE_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final String KICKOFF_USER_TEXT =
            "Rozpocznij rozmowę: przywitaj się i zapytaj, jakiej usługi potrzebuje klient.";

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            Jesteś AI handlowcem firmy "%s". Rozmawiasz z potencjalnym klientem, który opisuje \
            zlecenie, jakie chce zamówić. Klient może przesyłać zdjęcia (np. miejsca zlecenia, \
            uszkodzenia, przedmiotu) — przeanalizuj je, jeśli się pojawią. Twoim celem jest \
            zrozumieć, czego potrzebuje, i przygotować wstępną wycenę zgodną ze sposobem wyceny \
            tej firmy.

            Wiedza firmy o cenach, usługach i materiałach (ustalona przez właściciela):
            %s

            Zasady:
            - Na powitanie, zanim zapytasz o cokolwiek innego, wywołaj narzędzie suggest_options \
            z listą 2-5 głównych rodzajów usług tej firmy (na podstawie sposobu wyceny powyżej), \
            żeby klient mógł od razu kliknąć, czego potrzebuje, zamiast pisać od zera.
            - Materiały i urządzenia w wiedzy firmy powyżej to Twoja WEWNĘTRZNA wiedza do doboru \
            sprzętu i wyliczenia ceny — nigdy nie prezentuj ich klientowi jako listy do wyboru \
            (np. NIE pytaj "które z tych urządzeń Cię interesuje?" ani "czy może być urządzenie \
            X z naszej listy?"). Najpierw dowiedz się od klienta, czego faktycznie potrzebuje i \
            jakie ma wymagania (co chce osiągnąć, jaki zakres prac, warunki/parametry miejsca \
            zlecenia, jego oczekiwania) — dopiero na tej podstawie SAM dobierz odpowiedni \
            materiał/urządzenie z listy firmy do przygotowania wyceny, tak jak zrobiłby to \
            doświadczony handlowiec tej firmy. Konkretny dobrany materiał możesz wspomnieć \
            klientowi dopiero w podsumowaniu wyceny, nie jako pytanie do wyboru na starcie.
            - Zadawaj tylko potrzebne pytania o samo zlecenie — jedno lub dwa na raz. Gdy pytanie \
            o zakres prac, wariant usługi czy pilność ma naturalny, krótki zestaw odpowiedzi, \
            wywołaj suggest_options z 2-5 krótkimi opcjami do wyboru zamiast czekać na opis \
            tekstowy — klient zawsze może zamiast tego napisać własną odpowiedź.
            - Jeśli klient przesłał zdjęcia, weź pod uwagę to, co na nich widać, przy ocenie \
            zakresu prac.
            - Gdy masz wystarczające informacje, wywołaj narzędzie propose_quote z widełkami \
            cenowymi, walutą, krótkim uzasadnieniem oraz listą rzeczy, których nie byłeś pewien \
            przy wycenie (uncertain_factors) — może być pusta, jeśli wszystko było jasne.
            - Jeżeli usługa, o którą pyta klient, MIEŚCI SIĘ w zakresie usług tej firmy (wynika \
            to ze sposobu wyceny powyżej), ale brakuje części szczegółów do precyzyjnej wyceny, \
            i tak podaj orientacyjne widełki najlepsze, na jakie potrafisz WYŁĄCZNIE na podstawie \
            sposobu wyceny firmy powyżej, i opisz niepewności w uncertain_factors.
            - Jeżeli usługa, o którą pyta klient, W OGÓLE NIE JEST oferowana przez tę firmę (nie \
            wynika to ze sposobu wyceny firmy powyżej), NIE wywołuj propose_quote i pod żadnym \
            pozorem nie podawaj żadnej ceny ani widełek — nawet orientacyjnych, nawet opartych na \
            ogólnej wiedzy o rynku. Zamiast tego wprost i uprzejmie poinformuj klienta, że firma \
            nie świadczy takiej usługi.
            - Jeżeli firma NIE uzupełniła jeszcze sposobu wyceny (powyżej widnieje informacja \
            "brak"), nie masz żadnej podstawy nawet do orientacyjnej ceny. Zadaj najwyżej jedno \
            pytanie doprecyzowujące zlecenie, a następnie wywołaj propose_quote z bardzo szerokimi \
            widełkami odzwierciedlającymi tę niepewność (np. 0 jako min_price, jeśli naprawdę nie \
            masz punktu odniesienia), confidence bliskim 0 i jasnym wyjaśnieniem w reasoning oraz \
            uncertain_factors, że dokładna wycena wymaga kontaktu właściciela firmy.
            - Jeżeli klient wprost daje do zrozumienia, że nie ma więcej informacji do podania \
            (np. odpowiada "nie", "nic więcej", "to wszystko") — NIE zadawaj ponownie tego samego \
            albo podobnego pytania. Zakończ dopytywanie od razu: wywołaj propose_quote z tym, co \
            już wiesz (nawet jeśli to bardzo mało — patrz zasada wyżej), opisując brakujące \
            informacje w uncertain_factors zamiast dalej o nie prosić.
            - Nigdy nie szacuj ceny na podstawie ogólnej wiedzy o rynku czy internecie — jedynym \
            źródłem cen jest sposób wyceny firmy podany powyżej.
            - Po wywołaniu narzędzia krótko podsumuj wycenę dla klienta w rozmowie.
            - Pisz po polsku, w sposób przyjazny i profesjonalny.
            """;

    private static final String PROPOSE_QUOTE_TOOL_SCHEMA_JSON = """
            {
              "type": "object",
              "properties": {
                "min_price": { "type": "number", "description": "Dolna granica orientacyjnej wyceny." },
                "max_price": { "type": "number", "description": "Górna granica orientacyjnej wyceny." },
                "currency": { "type": "string", "description": "Waluta wyceny, np. PLN." },
                "reasoning": { "type": "string", "description": "Krótkie uzasadnienie wyceny, zrozumiałe dla klienta." },
                "uncertain_factors": {
                  "type": "array",
                  "items": { "type": "string" },
                  "description": "Informacje, których AI nie było pewne przy przygotowywaniu tej wyceny."
                }
              },
              "required": ["min_price", "max_price", "currency", "reasoning"]
            }
            """;

    private static final String SUGGEST_OPTIONS_TOOL_SCHEMA_JSON = """
            {
              "type": "object",
              "properties": {
                "options": {
                  "type": "array",
                  "items": { "type": "string" },
                  "description": "2-5 krótkich opcji do kliknięcia przez klienta, np. rodzaje usług albo warianty odpowiedzi."
                }
              },
              "required": ["options"]
            }
            """;

    private final CompanyService companyService;
    private final CompanyPricingProfileService profileService;
    private final PriceListItemRepository priceListItemRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final AttachmentRepository attachmentRepository;
    private final StorageService storageService;
    private final LeadService leadService;
    private final ConversationHistoryReader historyReader;
    private final DraftQuoteGenerator draftQuoteGenerator;
    private final LeadNotificationService leadNotificationService;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;
    private final QuoteRepository quoteRepository;

    public CompanyPublicResponse getCompanyPublicInfo(String slug) {
        Company company = companyService.getBySlug(slug);
        return new CompanyPublicResponse(
                company.getName(),
                company.getStatus() == CompanyStatus.ACTIVE,
                company.getDisplayName(),
                company.getResolvedPrimaryColor(),
                company.getResolvedWelcomeText(),
                company.hasLogo());
    }

    public LogoContent getCompanyLogo(String slug) {
        return companyService.getPublicLogo(slug);
    }

    /** Material NAMES only (never price) — this is an unauthenticated endpoint reachable
     * by anyone who knows the company's public slug, so it feeds the client chat's
     * autocomplete without exposing the owner's actual price list. */
    public List<String> getCompanyMaterialNames(String slug) {
        Company company = companyService.getBySlug(slug);
        return priceListItemRepository.findByCompanyIdOrderByCreatedAtDesc(company.getId()).stream()
                .map(PriceListItem::getName)
                .toList();
    }

    /** Blocks starting a new conversation once the company has used up its trial/plan
     * quote allowance for the current period, or (trial only) once the 7-day window has
     * elapsed regardless of how many of the 3 trial quotes were used — before any AI
     * cost is incurred, not after the client has already invested time chatting.
     * "Quote" here means every generated draft quote (see submitContact ->
     * draftQuoteGenerator.generate), the same definition BillingService uses for the
     * owner-facing usage display. */
    private void assertWithinPlanLimit(Company company) {
        if (company.isTrialExpired()) {
            throw new PlanLimitExceededException();
        }
        long used = quoteRepository.countByCompanyIdAndCreatedAtBetween(
                company.getId(), company.currentPeriodStart(), company.currentPeriodEnd());
        if (used >= company.quoteLimit()) {
            throw new PlanLimitExceededException();
        }
    }

    public StartConversationResponse startConversation(String slug) {
        Company company = companyService.getBySlug(slug);
        if (company.getStatus() != CompanyStatus.ACTIVE) {
            throw new CompanyNotReadyException();
        }
        assertWithinPlanLimit(company);
        Conversation conversation = conversationRepository.save(new Conversation(company.getId(), ConversationType.CLIENT_QUOTE));

        String systemPrompt = buildSystemPrompt(company.getName(), buildKnowledgeSummary(company.getId()));
        TurnOutcome outcome = runConversationTurn(new ArrayList<>(List.of(AiMessage.userText(KICKOFF_USER_TEXT))), systemPrompt);
        String greeting = outcome.text().isBlank() ? "Cześć! W czym mogę Ci dziś pomóc?" : outcome.text();
        messageRepository.save(new Message(conversation.getId(), MessageRole.ASSISTANT, greeting));

        return new StartConversationResponse(
                conversation.getId(), conversation.getPublicToken(), company.getName(), greeting, outcome.options());
    }

    public AttachmentUploadedResponse uploadAttachment(Long conversationId, String token, MultipartFile file) {
        Conversation conversation = requireActiveConversation(conversationId, token);

        if (file.isEmpty()) {
            throw new InvalidAttachmentException("Plik jest pusty.");
        }
        if (file.getSize() > MAX_IMAGE_SIZE_BYTES) {
            throw new InvalidAttachmentException("Zdjęcie jest za duże (limit 8 MB).");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_CONTENT_TYPES.contains(contentType)) {
            throw new InvalidAttachmentException("Obsługiwane są tylko zdjęcia JPEG, PNG lub WebP.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new InvalidAttachmentException("Nie udało się odczytać pliku.");
        }
        // Etap 21: the declared Content-Type above is client-controlled and trivially
        // spoofable — confirm the bytes actually are the image format claimed.
        if (!FileSignature.isImage(bytes, contentType)) {
            throw new InvalidAttachmentException("Plik nie jest prawidłowym zdjęciem JPEG, PNG ani WebP.");
        }

        String storageKey = storageService.store(
                conversation.getCompanyId(), file.getOriginalFilename(), contentType, new ByteArrayInputStream(bytes), bytes.length);

        Attachment attachment = attachmentRepository.save(new Attachment(
                conversation.getCompanyId(),
                conversation.getId(),
                AttachmentKind.IMAGE,
                storageKey,
                file.getOriginalFilename(),
                contentType,
                file.getSize()));

        return new AttachmentUploadedResponse(attachment.getId(), attachment.getOriginalFilename());
    }

    public QuoteMessageResponse sendMessage(Long conversationId, String token, String userText) {
        Conversation conversation = requireActiveConversation(conversationId, token);

        Message userMessage = messageRepository.save(new Message(conversation.getId(), MessageRole.USER, userText));

        List<Attachment> pendingAttachments = attachmentRepository.findByConversationIdAndMessageIdIsNull(conversation.getId());
        for (Attachment attachment : pendingAttachments) {
            attachment.attachToMessage(userMessage.getId());
        }
        attachmentRepository.saveAll(pendingAttachments);

        List<AiMessage> aiMessages = historyReader.buildHistory(conversation.getId());

        Company company = companyService.getById(conversation.getCompanyId());
        String systemPrompt = buildSystemPrompt(company.getName(), buildKnowledgeSummary(conversation.getCompanyId()));

        TurnOutcome outcome = runConversationTurn(aiMessages, systemPrompt);
        if (outcome.quote() != null) {
            conversation.setLastQuote(outcome.quoteJson().toString());
            conversationRepository.save(conversation);
        }

        String finalText = outcome.text().isBlank() ? "Dziękuję za informacje. Czy mogę jeszcze o coś dopytać?" : outcome.text();

        messageRepository.save(new Message(conversation.getId(), MessageRole.ASSISTANT, finalText));
        return new QuoteMessageResponse(finalText, outcome.quote(), outcome.options());
    }

    /**
     * Shared by sendMessage and the opening-greeting turn in startConversation — same
     * tool-use loop shape (propose_quote / suggest_options), just fed different history.
     */
    private record TurnOutcome(String text, QuoteDto quote, JsonNode quoteJson, List<String> options) {
    }

    private TurnOutcome runConversationTurn(List<AiMessage> aiMessages, String systemPrompt) {
        List<AiTool> tools = List.of(
                new AiTool(PROPOSE_QUOTE_TOOL_NAME, "Zapisuje wstępną wycenę zlecenia klienta.", readSchema(PROPOSE_QUOTE_TOOL_SCHEMA_JSON)),
                new AiTool(SUGGEST_OPTIONS_TOOL_NAME, "Proponuje klientowi krótkie opcje do kliknięcia.", readSchema(SUGGEST_OPTIONS_TOOL_SCHEMA_JSON)));

        QuoteDto quote = null;
        JsonNode quoteJson = null;
        List<String> options = List.of();
        // Anthropic sets stop_reason to "tool_use" whenever a turn includes a tool
        // call, even if that same turn ALSO includes explanatory text (e.g. "Niestety
        // nie świadczymy takiej usługi" alongside a suggest_options call showing what
        // the company DOES offer instead). Capturing text only from the final,
        // tool-free iteration silently dropped that earlier text — the client would
        // see just the generic fallback message. Every iteration's text is part of
        // what the model intended to say, so it's accumulated across the whole loop.
        StringBuilder textBuilder = new StringBuilder();

        for (int i = 0; i < MAX_TOOL_LOOP_ITERATIONS; i++) {
            AiTurnResult result = aiClient.sendMessage(systemPrompt, aiMessages, tools);

            String text = result.extractText();
            if (!text.isBlank()) {
                if (!textBuilder.isEmpty()) {
                    textBuilder.append("\n\n");
                }
                textBuilder.append(text);
            }

            if (!result.requiresToolExecution()) {
                break;
            }

            aiMessages.add(AiMessage.assistant(result.content()));

            List<AiContentBlock> toolResults = new ArrayList<>();
            for (AiToolUseBlock toolUse : result.extractToolUses()) {
                if (PROPOSE_QUOTE_TOOL_NAME.equals(toolUse.name())) {
                    quote = toQuoteDto(toolUse.input());
                    quoteJson = toolUse.input();
                    toolResults.add(new AiToolResultBlock(toolUse.id(), "Wycena zapisana."));
                } else if (SUGGEST_OPTIONS_TOOL_NAME.equals(toolUse.name())) {
                    options = toOptionsList(toolUse.input());
                    toolResults.add(new AiToolResultBlock(toolUse.id(), "Opcje zapisane."));
                } else {
                    toolResults.add(new AiToolResultBlock(toolUse.id(), "Nieznane narzędzie."));
                }
            }
            aiMessages.add(new AiMessage(AiRole.USER, toolResults));
        }

        return new TurnOutcome(textBuilder.toString(), quote, quoteJson, options);
    }

    public LeadCreatedResponse submitContact(Long conversationId, String token, SubmitContactRequest request) {
        Conversation conversation = requireActiveConversation(conversationId, token);

        if (conversation.getLastQuoteJson() == null) {
            throw new QuoteNotReadyException();
        }

        JsonNode quoteJson = readQuoteJson(conversation.getLastQuoteJson());
        Lead lead;
        try {
            lead = leadService.createFromConversation(
                    conversation.getCompanyId(),
                    conversation.getId(),
                    quoteJson,
                    request.name(),
                    request.phone(),
                    request.email());
        } catch (DataIntegrityViolationException e) {
            // Raced double-submit (double-click, retried request): another concurrent
            // call already created the lead for this conversation and is handling
            // completion/draft-quote generation/notification itself — just hand back
            // that lead instead of erroring or duplicating those side effects.
            return new LeadCreatedResponse(leadService.getByConversationId(conversation.getId()).getId());
        }

        conversation.complete();
        conversationRepository.save(conversation);

        draftQuoteGenerator.generate(lead.getId());
        leadNotificationService.notifyOwnerOfNewLead(lead.getId());

        return new LeadCreatedResponse(lead.getId());
    }

    private Conversation requireConversation(Long conversationId, String token) {
        return conversationRepository.findByIdAndPublicToken(conversationId, token)
                .filter(conversation -> conversation.getType() == ConversationType.CLIENT_QUOTE)
                .orElseThrow(() -> new ConversationNotFoundException(conversationId));
    }

    private Conversation requireActiveConversation(Long conversationId, String token) {
        Conversation conversation = requireConversation(conversationId, token);
        if (conversation.getStatus() != ConversationStatus.ACTIVE) {
            throw new ConversationNotFoundException(conversationId);
        }
        return conversation;
    }

    private QuoteDto toQuoteDto(JsonNode input) {
        List<String> uncertainFactors = new ArrayList<>();
        JsonNode factorsNode = input.path("uncertain_factors");
        if (factorsNode.isArray()) {
            factorsNode.forEach(node -> uncertainFactors.add(node.asText()));
        }
        return new QuoteDto(
                input.path("min_price").asDouble(),
                input.path("max_price").asDouble(),
                input.path("currency").asText("PLN"),
                input.path("reasoning").asText(""),
                uncertainFactors);
    }

    private List<String> toOptionsList(JsonNode input) {
        List<String> options = new ArrayList<>();
        JsonNode optionsNode = input.path("options");
        if (optionsNode.isArray()) {
            optionsNode.forEach(node -> options.add(node.asText()));
        }
        return options;
    }

    /**
     * Combines cennik/services and "Moje materiały" into one block of knowledge for the
     * prompt. Cennik is no longer required — a company can rely on materials alone (or
     * vice versa) — so this returns blank only when BOTH are empty, which is exactly the
     * condition buildSystemPrompt already treats as "brak" (triggering the wide-range,
     * low-confidence, "wymaga kontaktu właściciela" fallback in the system prompt rules).
     */
    private String buildKnowledgeSummary(Long companyId) {
        PricingProfileData pricingData = profileService.getData(companyId);
        List<PriceListItem> materials = priceListItemRepository.findByCompanyIdOrderByCreatedAtDesc(companyId);

        boolean hasPricing = PricingProfileFormatter.hasContent(pricingData);
        boolean hasMaterials = !materials.isEmpty();
        if (!hasPricing && !hasMaterials) {
            return "";
        }

        StringBuilder summary = new StringBuilder();
        if (hasPricing) {
            summary.append(PricingProfileFormatter.toPromptText(pricingData));
        }
        if (hasMaterials) {
            if (!summary.isEmpty()) {
                summary.append("\n\n");
            }
            summary.append(PriceListFormatter.toPromptText(materials));
        }
        return summary.toString();
    }

    private String buildSystemPrompt(String companyName, String profileSummary) {
        String known = (profileSummary == null || profileSummary.isBlank())
                ? "brak — potraktuj to jako informację, że firma nie uzupełniła jeszcze szczegółów."
                : profileSummary;
        return SYSTEM_PROMPT_TEMPLATE.formatted(companyName, known);
    }

    private JsonNode readSchema(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid tool schema", e);
        }
    }

    private JsonNode readQuoteJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid stored quote JSON", e);
        }
    }
}
