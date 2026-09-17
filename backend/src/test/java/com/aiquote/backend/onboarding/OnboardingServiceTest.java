package com.aiquote.backend.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiquote.backend.ai.AiClient;
import com.aiquote.backend.ai.AiTextBlock;
import com.aiquote.backend.ai.AiToolUseBlock;
import com.aiquote.backend.ai.AiTurnResult;
import com.aiquote.backend.company.CompanyService;
import com.aiquote.backend.conversation.Conversation;
import com.aiquote.backend.conversation.ConversationRepository;
import com.aiquote.backend.conversation.ConversationType;
import com.aiquote.backend.conversation.MessageRepository;
import com.aiquote.backend.knowledgebase.CompanyPricingProfileService;
import com.aiquote.backend.knowledgebase.PriceListItemRepository;
import com.aiquote.backend.knowledgebase.PriceListItemService;
import com.aiquote.backend.knowledgebase.PriceListItemSource;
import com.aiquote.backend.knowledgebase.PriceListItemTool;
import com.aiquote.backend.knowledgebase.PricingProfileData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Covers the "chat learns a material, saves it" behavior added alongside the existing
 * update_pricing_profile flow: when the owner mentions a concrete material/device while
 * chatting in "Wiedza firmy", the same AI turn can call save_price_list_items and it
 * lands in PriceListItemService — a separate list from cennik/services (see
 * PriceListItem's javadoc), reached through the SAME conversation, not a separate UI.
 */
@ExtendWith(MockitoExtension.class)
class OnboardingServiceTest {

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private CompanyPricingProfileService profileService;
    @Mock
    private PriceListItemRepository priceListItemRepository;
    @Mock
    private PriceListItemService priceListItemService;
    @Mock
    private CompanyService companyService;
    @Mock
    private AiClient aiClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OnboardingService service;

    @BeforeEach
    void setUp() {
        service = new OnboardingService(
                conversationRepository, messageRepository, profileService, priceListItemRepository,
                priceListItemService, companyService, aiClient, objectMapper);
    }

    @Test
    void sendMessageSavesMaterialsWhenAiCallsSavePriceListItemsTool() throws Exception {
        Conversation conversation = new Conversation(5L, ConversationType.ONBOARDING);
        ReflectionTestUtils.setField(conversation, "id", 1L);
        when(conversationRepository.findByIdAndCompanyId(1L, 5L)).thenReturn(Optional.of(conversation));
        when(profileService.getData(5L)).thenReturn(PricingProfileData.empty());

        JsonNode toolInput = objectMapper.readTree("{\"items\":[{\"name\":\"Rura PVC 50mm\",\"category\":null,\"price\":25,\"unit\":\"mb\"}]}");
        AiTurnResult toolCall = new AiTurnResult(List.of(new AiToolUseBlock("t1", PriceListItemTool.NAME, toolInput)), "tool_use");
        AiTurnResult finalTurn = new AiTurnResult(List.of(new AiTextBlock("Dzięki, zapisałem tę rurę na Twojej liście materiałów.")), "end_turn");
        when(aiClient.sendMessage(any(), any(), any())).thenReturn(toolCall, finalTurn);

        SendMessageResponse response = service.sendMessage(5L, 1L, "używam rur PVC 50mm po 25 zł za mb");

        verify(priceListItemService).upsertAllFromToolInput(eq(5L), eq(toolInput), eq(PriceListItemSource.CHAT));
        assertThat(response.reply()).isEqualTo("Dzięki, zapisałem tę rurę na Twojej liście materiałów.");
        // Materials are a separate concept from the pricing profile — saving one must not
        // be reported back as a services/cennik update.
        assertThat(response.profileUpdated()).isFalse();
    }
}
