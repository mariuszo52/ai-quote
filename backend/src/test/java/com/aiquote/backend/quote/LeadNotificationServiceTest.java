package com.aiquote.backend.quote;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiquote.backend.auth.AppUser;
import com.aiquote.backend.auth.AppUserRepository;
import com.aiquote.backend.email.EmailSendException;
import com.aiquote.backend.email.EmailService;
import com.aiquote.backend.lead.Lead;
import com.aiquote.backend.lead.LeadRepository;
import com.aiquote.backend.tenant.UserRole;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Covers Etap 16's owner-notification rules: the email carries only the client's name
 * and short summary (never the transcript/photos), it's addressed to the right
 * company's owner, and nothing here ever throws back out — a missing lead/owner/SMTP
 * failure is logged and swallowed, since this runs @Async off the request that created
 * the lead and must never affect it.
 */
@ExtendWith(MockitoExtension.class)
class LeadNotificationServiceTest {

    @Mock
    private LeadRepository leadRepository;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private EmailService emailService;

    private LeadNotificationService service;

    @BeforeEach
    void setUp() {
        service = new LeadNotificationService(leadRepository, appUserRepository, emailService, "https://app.example.com");
    }

    @Test
    void sendsOwnerNotificationWithClientNameAndSummaryOnly() {
        Lead lead = lead(5L, "Jan Kowalski", "Malowanie ścian w salonie.");
        when(leadRepository.findById(1L)).thenReturn(Optional.of(lead));
        when(appUserRepository.findFirstByCompanyIdOrderByIdAsc(5L)).thenReturn(Optional.of(appUser("owner@example.com")));

        service.notifyOwnerOfNewLead(1L);

        verify(emailService).send(argThat(message ->
                message.to().equals("owner@example.com")
                        && message.subject().contains("Jan Kowalski")
                        && message.body().contains("Malowanie ścian w salonie.")
                        && message.body().contains("https://app.example.com/app/leads/1")
                        && message.attachment() == null));
    }

    @Test
    void looksUpTheOwnerForTheLeadsOwnCompanyNotAnyOther() {
        Lead lead = lead(42L, "Anna Nowak", "Naprawa dachu.");
        when(leadRepository.findById(1L)).thenReturn(Optional.of(lead));
        when(appUserRepository.findFirstByCompanyIdOrderByIdAsc(42L)).thenReturn(Optional.of(appUser("owner42@example.com")));

        service.notifyOwnerOfNewLead(1L);

        verify(appUserRepository).findFirstByCompanyIdOrderByIdAsc(42L);
        verify(emailService).send(argThat(message -> message.to().equals("owner42@example.com")));
    }

    @Test
    void doesNothingWhenLeadIsMissing() {
        when(leadRepository.findById(1L)).thenReturn(Optional.empty());

        service.notifyOwnerOfNewLead(1L);

        verify(emailService, never()).send(any());
    }

    @Test
    void doesNothingWhenNoOwnerAccountExistsForTheCompany() {
        Lead lead = lead(5L, "Jan Kowalski", "Opis.");
        when(leadRepository.findById(1L)).thenReturn(Optional.of(lead));
        when(appUserRepository.findFirstByCompanyIdOrderByIdAsc(5L)).thenReturn(Optional.empty());

        service.notifyOwnerOfNewLead(1L);

        verify(emailService, never()).send(any());
    }

    @Test
    void swallowsSmtpFailuresWithoutThrowing() {
        Lead lead = lead(5L, "Jan Kowalski", "Opis.");
        when(leadRepository.findById(1L)).thenReturn(Optional.of(lead));
        when(appUserRepository.findFirstByCompanyIdOrderByIdAsc(5L)).thenReturn(Optional.of(appUser("owner@example.com")));
        org.mockito.Mockito.doThrow(new EmailSendException("SMTP timeout", new RuntimeException()))
                .when(emailService).send(any());

        service.notifyOwnerOfNewLead(1L); // must not throw — JUnit fails the test automatically if it does
    }

    @Test
    void fallsBackToAGenericLineWhenNoSummaryIsAvailable() {
        Lead lead = lead(5L, "Jan Kowalski", null);
        when(leadRepository.findById(1L)).thenReturn(Optional.of(lead));
        when(appUserRepository.findFirstByCompanyIdOrderByIdAsc(5L)).thenReturn(Optional.of(appUser("owner@example.com")));

        service.notifyOwnerOfNewLead(1L);

        verify(emailService).send(argThat(message -> !message.body().isBlank()));
    }

    private Lead lead(long companyId, String clientName, String aiSummary) {
        Lead lead = new Lead(companyId, 3L, clientName, "600100200", "klient@example.com", aiSummary, null, null, "PLN", null);
        ReflectionTestUtils.setField(lead, "id", 1L);
        return lead;
    }

    private AppUser appUser(String email) {
        return AppUser.local(1L, email, "hash", UserRole.OWNER);
    }
}
