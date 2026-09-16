package com.aiquote.backend.feedback;

import com.aiquote.backend.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Owner-facing (authenticated, tenant-scoped) AI-vs-final comparison for an approved quote (Etap 15). */
@RestController
@RequestMapping("/api/quotes/{id}/feedback")
@RequiredArgsConstructor
public class QuoteFeedbackController {

    private final QuoteFeedbackService feedbackService;

    @GetMapping
    public QuoteFeedbackResponse get(@PathVariable Long id) {
        return feedbackService.getByQuoteId(TenantContext.currentCompanyId(), id);
    }

    @PutMapping
    public QuoteFeedbackResponse submit(@PathVariable Long id, @RequestBody SubmitFeedbackRequest request) {
        return feedbackService.submitFeedback(TenantContext.currentCompanyId(), id, request);
    }
}
