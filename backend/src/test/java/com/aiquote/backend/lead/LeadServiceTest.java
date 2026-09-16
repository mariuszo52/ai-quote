package com.aiquote.backend.lead;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.aiquote.backend.conversation.AttachmentRepository;
import com.aiquote.backend.conversation.MessageRepository;
import com.aiquote.backend.file.StorageService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Covers Etap 14's transition-validation requirement: the PATCH endpoint must reject
 * any status change LeadStatusTransitions doesn't allow, and every lookup stays
 * tenant-scoped.
 */
@ExtendWith(MockitoExtension.class)
class LeadServiceTest {

    @Mock
    private LeadRepository leadRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private AttachmentRepository attachmentRepository;
    @Mock
    private StorageService storageService;

    private LeadService service;

    @BeforeEach
    void setUp() {
        service = new LeadService(leadRepository, messageRepository, attachmentRepository, storageService);
    }

    @Test
    void allowsAValidTransition() {
        Lead lead = lead(LeadStatus.NEW);
        when(leadRepository.findByIdAndCompanyId(1L, 5L)).thenReturn(Optional.of(lead));

        LeadStatusResponse response = service.updateStatus(5L, 1L, LeadStatus.CONTACTED);

        assertThat(response.status()).isEqualTo("CONTACTED");
        assertThat(lead.getStatus()).isEqualTo(LeadStatus.CONTACTED);
    }

    @Test
    void rejectsAnInvalidTransition() {
        Lead lead = lead(LeadStatus.WON);
        when(leadRepository.findByIdAndCompanyId(1L, 5L)).thenReturn(Optional.of(lead));

        assertThatThrownBy(() -> service.updateStatus(5L, 1L, LeadStatus.CONTACTED))
                .isInstanceOf(InvalidLeadStatusException.class);
        assertThat(lead.getStatus()).isEqualTo(LeadStatus.WON);
    }

    @Test
    void rejectsManuallySettingQuoteSent() {
        Lead lead = lead(LeadStatus.CONTACTED);
        when(leadRepository.findByIdAndCompanyId(1L, 5L)).thenReturn(Optional.of(lead));

        assertThatThrownBy(() -> service.updateStatus(5L, 1L, LeadStatus.QUOTE_SENT))
                .isInstanceOf(InvalidLeadStatusException.class);
    }

    @Test
    void updateStatusIsTenantScoped() {
        when(leadRepository.findByIdAndCompanyId(1L, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus(999L, 1L, LeadStatus.CONTACTED))
                .isInstanceOf(LeadNotFoundException.class);
    }

    private Lead lead(LeadStatus status) {
        Lead lead = new Lead(5L, 3L, "Jan Kowalski", "600100200", "jan@example.com", "Opis", null, null, "PLN", null);
        ReflectionTestUtils.setField(lead, "id", 1L);
        ReflectionTestUtils.setField(lead, "status", status);
        return lead;
    }
}
