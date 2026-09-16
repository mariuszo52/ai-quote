package com.aiquote.backend.quote;

import com.aiquote.backend.auth.AppUser;
import com.aiquote.backend.auth.AppUserRepository;
import com.aiquote.backend.email.EmailMessage;
import com.aiquote.backend.email.EmailService;
import com.aiquote.backend.lead.Lead;
import com.aiquote.backend.lead.LeadRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Notifies the company owner by email as soon as a new Lead is created (Etap 16) — a
 * separate bean (not a method on QuoteAgentService), same reasoning as
 * DraftQuoteGenerator: @Async only actually goes through the Spring proxy when called
 * on a different bean, and this must never slow down the client's submitContact
 * request. Lives here rather than in the lead package for the same reason
 * quote.LeadOverviewController does: sending an email needs auth (owner lookup) and
 * email, and lead must not depend on either — see LeadService's own class javadoc.
 * Deliberately sends only the client's name and Lead's short aiSummary, never the full
 * transcript or photos (Etap 16 #3's privacy requirement).
 */
@Service
@Slf4j
public class LeadNotificationService {

    private final LeadRepository leadRepository;
    private final AppUserRepository appUserRepository;
    private final EmailService emailService;
    private final String frontendUrl;

    public LeadNotificationService(
            LeadRepository leadRepository,
            AppUserRepository appUserRepository,
            EmailService emailService,
            @Value("${app.frontend-url}") String frontendUrl) {
        this.leadRepository = leadRepository;
        this.appUserRepository = appUserRepository;
        this.emailService = emailService;
        this.frontendUrl = frontendUrl;
    }

    @Async
    public void notifyOwnerOfNewLead(Long leadId) {
        try {
            Lead lead = leadRepository.findById(leadId).orElse(null);
            if (lead == null) {
                log.warn("Cannot send new-lead notification: lead {} not found", leadId);
                return;
            }
            String ownerEmail = appUserRepository.findFirstByCompanyIdOrderByIdAsc(lead.getCompanyId())
                    .map(AppUser::getEmail)
                    .orElse(null);
            if (ownerEmail == null) {
                log.warn("Cannot send new-lead notification: no owner account found for company {}", lead.getCompanyId());
                return;
            }

            String link = frontendUrl + "/app/leads/" + lead.getId();
            emailService.send(new EmailMessage(ownerEmail, subject(lead), body(lead, link), null));
        } catch (Exception e) {
            log.warn("Failed to send new-lead notification for lead {}", leadId, e);
        }
    }

    private String subject(Lead lead) {
        return "Nowe zapytanie: " + lead.getClientName();
    }

    private String body(Lead lead, String link) {
        String summary = lead.getAiSummary() != null && !lead.getAiSummary().isBlank()
                ? lead.getAiSummary()
                : "Klient nie podał dodatkowego opisu.";
        return """
                Cześć,

                Otrzymałeś nowe zapytanie od klienta: %s (%s).

                %s

                Sprawdź szczegóły i wycenę w panelu:
                %s
                """.formatted(lead.getClientName(), lead.getClientPhone(), summary, link);
    }
}
