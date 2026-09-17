package com.aiquote.backend.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiquote.backend.ai.AiClient;
import com.aiquote.backend.company.Company;
import com.aiquote.backend.company.CompanyService;
import com.aiquote.backend.company.CompanyStatus;
import com.aiquote.backend.company.PlanLimitExceededException;
import com.aiquote.backend.conversation.AttachmentRepository;
import com.aiquote.backend.conversation.Conversation;
import com.aiquote.backend.conversation.ConversationRepository;
import com.aiquote.backend.conversation.ConversationType;
import com.aiquote.backend.conversation.MessageRepository;
import com.aiquote.backend.file.StorageService;
import com.aiquote.backend.ai.AiTextBlock;
import com.aiquote.backend.ai.AiTurnResult;
import com.aiquote.backend.knowledgebase.CompanyPricingProfileService;
import com.aiquote.backend.knowledgebase.PriceListItem;
import com.aiquote.backend.knowledgebase.PriceListItemRepository;
import com.aiquote.backend.knowledgebase.PriceListItemSource;
import com.aiquote.backend.knowledgebase.PricingProfileData;
import com.aiquote.backend.lead.Lead;
import com.aiquote.backend.lead.LeadService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Covers Etap 20's fix for a raced double-submit of the public contact form: two
 * near-simultaneous requests could both pass requireActiveConversation before either
 * committed, so the leads.conversation_id unique constraint (V15) is the real guard.
 * A DataIntegrityViolationException from that constraint must be recovered as "return
 * the winning request's lead" — never surfaced as an error, and never allowed to
 * trigger a second draft-quote generation or owner notification for one conversation.
 */
@ExtendWith(MockitoExtension.class)
class QuoteAgentServiceTest {

    @Mock
    private CompanyService companyService;
    @Mock
    private CompanyPricingProfileService profileService;
    @Mock
    private PriceListItemRepository priceListItemRepository;
    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private AttachmentRepository attachmentRepository;
    @Mock
    private StorageService storageService;
    @Mock
    private LeadService leadService;
    @Mock
    private ConversationHistoryReader historyReader;
    @Mock
    private DraftQuoteGenerator draftQuoteGenerator;
    @Mock
    private LeadNotificationService leadNotificationService;
    @Mock
    private AiClient aiClient;
    @Mock
    private QuoteRepository quoteRepository;

    private QuoteAgentService service;

    @BeforeEach
    void setUp() {
        service = new QuoteAgentService(
                companyService,
                profileService,
                priceListItemRepository,
                conversationRepository,
                messageRepository,
                attachmentRepository,
                storageService,
                leadService,
                historyReader,
                draftQuoteGenerator,
                leadNotificationService,
                aiClient,
                new ObjectMapper(),
                quoteRepository);
    }

    @Test
    void blocksStartingAConversationOnceTheTrialQuoteLimitIsReached() {
        Company company = new Company("Firma", "firma", CompanyStatus.ACTIVE);
        ReflectionTestUtils.setField(company, "id", 5L);
        ReflectionTestUtils.setField(company, "createdAt", Instant.now());
        when(companyService.getBySlug("firma")).thenReturn(company);
        // TRIAL allows 3 quotes — 3 already used means the limit is reached.
        when(quoteRepository.countByCompanyIdAndCreatedAtBetween(eq(5L), any(), any())).thenReturn(3L);

        assertThatThrownBy(() -> service.startConversation("firma")).isInstanceOf(PlanLimitExceededException.class);

        verify(conversationRepository, never()).save(any());
        verify(aiClient, never()).sendMessage(any(), any(), any());
    }

    @Test
    void blocksStartingAConversationOnceTheSevenDayTrialWindowHasElapsedEvenWithQuotesToSpare() {
        Company company = new Company("Firma", "firma", CompanyStatus.ACTIVE);
        ReflectionTestUtils.setField(company, "id", 5L);
        ReflectionTestUtils.setField(company, "createdAt", Instant.now().minus(java.time.Duration.ofDays(8)));
        when(companyService.getBySlug("firma")).thenReturn(company);
        // The 7-day window alone is enough to block — never even gets to counting quotes.

        assertThatThrownBy(() -> service.startConversation("firma")).isInstanceOf(PlanLimitExceededException.class);

        verify(conversationRepository, never()).save(any());
        verify(aiClient, never()).sendMessage(any(), any(), any());
    }

    @Test
    void startConversationIncludesMaterialsInTheSystemPromptAlongsideServices() {
        Company company = new Company("Firma", "firma", CompanyStatus.ACTIVE);
        ReflectionTestUtils.setField(company, "id", 5L);
        ReflectionTestUtils.setField(company, "createdAt", Instant.now());
        when(companyService.getBySlug("firma")).thenReturn(company);
        when(quoteRepository.countByCompanyIdAndCreatedAtBetween(eq(5L), any(), any())).thenReturn(0L);
        when(conversationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(profileService.getData(5L)).thenReturn(PricingProfileData.empty());

        PriceListItem material = new PriceListItem(5L, "Klimatyzator Daikin X", null, 3500.0, "szt", PriceListItemSource.MANUAL);
        when(priceListItemRepository.findByCompanyIdOrderByCreatedAtDesc(5L)).thenReturn(java.util.List.of(material));

        when(aiClient.sendMessage(any(), any(), any()))
                .thenReturn(new AiTurnResult(java.util.List.of(new AiTextBlock("Cześć!")), "end_turn"));

        service.startConversation("firma");

        ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiClient).sendMessage(systemPromptCaptor.capture(), any(), any());
        assertThat(systemPromptCaptor.getValue()).contains("Klimatyzator Daikin X");
    }

    @Test
    void racedDoubleSubmitReturnsTheWinningLeadWithoutDuplicatingSideEffects() {
        Conversation conversation = new Conversation(5L, ConversationType.CLIENT_QUOTE);
        ReflectionTestUtils.setField(conversation, "id", 42L);
        conversation.setLastQuote("{\"min_price\":100,\"max_price\":200,\"currency\":\"PLN\",\"reasoning\":\"ok\"}");
        when(conversationRepository.findByIdAndPublicToken(42L, "tok")).thenReturn(Optional.of(conversation));

        when(leadService.createFromConversation(any(), any(), any(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        Lead existingLead = new Lead(5L, 42L, "Jan", "111111111", null, "reasoning", 100.0, 200.0, "PLN", null);
        ReflectionTestUtils.setField(existingLead, "id", 99L);
        when(leadService.getByConversationId(42L)).thenReturn(existingLead);

        LeadCreatedResponse response = service.submitContact(42L, "tok", new SubmitContactRequest("Jan", "111111111", null));

        assertThat(response.leadId()).isEqualTo(99L);
        verify(draftQuoteGenerator, never()).generate(any());
        verify(leadNotificationService, never()).notifyOwnerOfNewLead(any());
        verify(conversationRepository, never()).save(any());
    }
}
