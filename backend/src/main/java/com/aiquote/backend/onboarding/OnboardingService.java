package com.aiquote.backend.onboarding;

import com.aiquote.backend.ai.AiClient;
import com.aiquote.backend.ai.AiContentBlock;
import com.aiquote.backend.ai.AiMessage;
import com.aiquote.backend.ai.AiRole;
import com.aiquote.backend.ai.AiTextBlock;
import com.aiquote.backend.ai.AiTool;
import com.aiquote.backend.ai.AiToolResultBlock;
import com.aiquote.backend.ai.AiToolUseBlock;
import com.aiquote.backend.ai.AiTurnResult;
import com.aiquote.backend.company.CompanyResponse;
import com.aiquote.backend.company.CompanyService;
import com.aiquote.backend.conversation.Conversation;
import com.aiquote.backend.conversation.ConversationRepository;
import com.aiquote.backend.conversation.ConversationStatus;
import com.aiquote.backend.conversation.ConversationType;
import com.aiquote.backend.conversation.Message;
import com.aiquote.backend.conversation.MessageDto;
import com.aiquote.backend.conversation.MessageRepository;
import com.aiquote.backend.conversation.MessageRole;
import com.aiquote.backend.knowledgebase.CompanyPricingProfileService;
import com.aiquote.backend.knowledgebase.PricingProfileData;
import com.aiquote.backend.knowledgebase.PricingProfileFormatter;
import com.aiquote.backend.knowledgebase.PricingProfileTool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the onboarding chat: rebuilds history for each turn, drives the
 * update_pricing_profile tool-use loop, and persists only the plain-text turns
 * (tool calls stay ephemeral, scoped to a single sendMessage invocation).
 *
 * Deliberately not @Transactional at the class/method level: the AI call is a slow
 * external HTTP request, and each repository/service call below already manages its
 * own transaction, so we avoid holding a DB connection open for the duration of the
 * LLM round-trip.
 */
@Service
@RequiredArgsConstructor
public class OnboardingService {

    private static final int MAX_TOOL_LOOP_ITERATIONS = 4;
    private static final String KICKOFF_USER_TEXT =
            "Rozpocznij rozmowę: przywitaj się i zadaj pierwsze pytanie, żeby dowiedzieć się, czym zajmuje się firma.";

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            Jesteś asystentem AI, który podczas rozmowy z właścicielem firmy usługowej pomaga \
            mu opisać, w jaki sposób wycenia swoje usługi. Twoim celem jest zbudowanie pełnego, \
            zrozumiałego opisu zasad wyceny tej konkretnej firmy — niezależnie od branży.

            Zasady:
            - Zadawaj tylko konkretne, potrzebne pytania — jedno lub dwa na raz, nie zarzucaj \
            rozmówcy długą listą naraz.
            - Pytaj o: rodzaje świadczonych usług, sposób naliczania ceny (stawka godzinowa, \
            za jednostkę, za projekt, czy wycena indywidualna), czynniki wpływające na cenę \
            (odległość, materiały, trudność, pilność), typowe widełki cenowe, minimalne opłaty.
            - NIGDY nie zgaduj ani nie wymyślaj konkretnych liczb (cen, stawek, widełek). Zapisuj \
            wartość liczbową TYLKO wtedy, gdy właściciel faktycznie ją podał w rozmowie — w \
            przeciwnym razie zostaw pole puste.
            - Za każdym razem, gdy dowiesz się czegoś nowego i konkretnego o jednej lub kilku \
            usługach, wywołaj narzędzie update_pricing_profile — podaj TYLKO usługi, o których \
            dowiedziałeś się czegoś nowego w tej turze (nie musisz powtarzać całej dotychczasowej \
            wiedzy, jest ona bezpiecznie scalana automatycznie z tym, co już wiadomo).
            - Po wywołaniu narzędzia krótko potwierdź w rozmowie, czego się nauczyłeś, i zadaj \
            kolejne pytanie.
            - Pisz po polsku, w sposób przyjazny i rzeczowy.

            Dotychczas poznany profil wyceny firmy:
            %s
            """;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final CompanyPricingProfileService profileService;
    private final CompanyService companyService;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    public StartSessionResponse startOrResumeSession(Long companyId) {
        Conversation conversation = conversationRepository
                .findFirstByCompanyIdAndTypeAndStatusOrderByCreatedAtDesc(
                        companyId, ConversationType.ONBOARDING, ConversationStatus.ACTIVE)
                .orElseGet(() -> conversationRepository.save(new Conversation(companyId, ConversationType.ONBOARDING)));

        List<Message> history = messageRepository.findByConversationIdOrderByIdAsc(conversation.getId());
        if (history.isEmpty()) {
            String opening = generateOpeningMessage(companyId);
            history = List.of(messageRepository.save(new Message(conversation.getId(), MessageRole.ASSISTANT, opening)));
        }

        List<MessageDto> messages = history.stream()
                .map(message -> new MessageDto(message.getRole().name(), message.getContent(), message.getCreatedAt()))
                .toList();

        return new StartSessionResponse(conversation.getId(), messages);
    }

    /**
     * The AI needs to speak first so the contractor isn't left guessing what to type —
     * a synthetic, unpersisted kickoff turn gets it to open with a question instead.
     */
    private String generateOpeningMessage(Long companyId) {
        String systemPrompt = buildSystemPrompt(companyId);
        AiTurnResult result = aiClient.sendMessage(systemPrompt, List.of(AiMessage.userText(KICKOFF_USER_TEXT)), List.of());
        String text = result.extractText();
        return text.isBlank() ? "Cześć! Opowiedz mi, czym zajmuje się Twoja firma i jak zwykle wyceniasz zlecenia." : text;
    }

    public SendMessageResponse sendMessage(Long companyId, Long conversationId, String userText) {
        Conversation conversation = conversationRepository.findByIdAndCompanyId(conversationId, companyId)
                .orElseThrow(() -> new ConversationNotFoundException(conversationId));

        messageRepository.save(new Message(conversation.getId(), MessageRole.USER, userText));

        List<AiMessage> aiMessages = new ArrayList<>();
        for (Message message : messageRepository.findByConversationIdOrderByIdAsc(conversation.getId())) {
            if (message.getRole() == MessageRole.USER) {
                aiMessages.add(AiMessage.userText(message.getContent()));
            } else if (message.getRole() == MessageRole.ASSISTANT) {
                aiMessages.add(AiMessage.assistant(List.of(new AiTextBlock(message.getContent()))));
            }
        }

        String systemPrompt = buildSystemPrompt(companyId);
        List<AiTool> tools = List.of(PricingProfileTool.definition(
                "Zapisuje informacje o cenach jednej lub kilku usług, poznane w tej turze rozmowy.", objectMapper));

        boolean profileUpdated = false;
        String finalText = "";

        for (int i = 0; i < MAX_TOOL_LOOP_ITERATIONS; i++) {
            AiTurnResult result = aiClient.sendMessage(systemPrompt, aiMessages, tools);

            if (!result.requiresToolExecution()) {
                finalText = result.extractText();
                break;
            }

            aiMessages.add(AiMessage.assistant(result.content()));

            List<AiContentBlock> toolResults = new ArrayList<>();
            for (AiToolUseBlock toolUse : result.extractToolUses()) {
                if (PricingProfileTool.NAME.equals(toolUse.name())) {
                    profileService.mergeAiUpdate(companyId, readIncomingProfile(toolUse.input()));
                    profileUpdated = true;
                    toolResults.add(new AiToolResultBlock(toolUse.id(), "Profil zapisany."));
                } else {
                    toolResults.add(new AiToolResultBlock(toolUse.id(), "Nieznane narzędzie."));
                }
            }
            aiMessages.add(new AiMessage(AiRole.USER, toolResults));
        }

        if (finalText.isBlank()) {
            finalText = "Dziękuję za informacje. Kontynuujmy — opowiedz więcej o swoich usługach.";
        }

        messageRepository.save(new Message(conversation.getId(), MessageRole.ASSISTANT, finalText));
        return new SendMessageResponse(finalText, profileUpdated);
    }

    public ProfileResponse getProfile(Long companyId) {
        var profile = profileService.getOrCreate(companyId);
        PricingProfileData data = profileService.getData(companyId);
        return new ProfileResponse(data.services(), data.generalNotes(), profile.getUpdatedAt(), profile.getManuallyEditedAt());
    }

    public ProfileResponse updateProfileManually(Long companyId, UpdateProfileRequest request) {
        PricingProfileData replacement = new PricingProfileData(request.services(), request.generalNotes());
        var profile = profileService.replaceManually(companyId, replacement);
        return new ProfileResponse(replacement.services(), replacement.generalNotes(), profile.getUpdatedAt(), profile.getManuallyEditedAt());
    }

    public CompanyResponse completeOnboarding(Long companyId) {
        return CompanyResponse.from(companyService.activate(companyId));
    }

    private String buildSystemPrompt(Long companyId) {
        return SYSTEM_PROMPT_TEMPLATE.formatted(PricingProfileFormatter.toPromptText(profileService.getData(companyId)));
    }

    private PricingProfileData readIncomingProfile(JsonNode input) {
        try {
            return objectMapper.treeToValue(input, PricingProfileData.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid update_pricing_profile tool input", e);
        }
    }
}
