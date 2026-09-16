package com.aiquote.backend.onboarding;

import com.aiquote.backend.company.CompanyResponse;
import com.aiquote.backend.knowledgebase.KnowledgeIngestionService;
import com.aiquote.backend.knowledgebase.KnowledgeSourceResponse;
import com.aiquote.backend.tenant.TenantContext;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private static final long SSE_TIMEOUT_MS = 60_000L;

    private final OnboardingService onboardingService;
    private final OnboardingStreamingHandler streamingHandler;
    private final KnowledgeIngestionService knowledgeIngestionService;

    @PostMapping("/sessions")
    public StartSessionResponse startSession() {
        return onboardingService.startOrResumeSession(TenantContext.currentCompanyId());
    }

    @PostMapping(value = "/sessions/{id}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessage(@PathVariable Long id, @Valid @RequestBody SendMessageRequest request) {
        // Read the tenant off this request thread — the streaming work below runs on a
        // separate @Async thread where SecurityContextHolder's ThreadLocal isn't populated.
        Long companyId = TenantContext.currentCompanyId();
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        streamingHandler.stream(emitter, companyId, id, request.content());
        return emitter;
    }

    @GetMapping("/profile")
    public ProfileResponse getProfile() {
        return onboardingService.getProfile(TenantContext.currentCompanyId());
    }

    @PatchMapping("/profile")
    public ProfileResponse updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return onboardingService.updateProfileManually(TenantContext.currentCompanyId(), request);
    }

    @PostMapping("/complete")
    public CompanyResponse complete() {
        return onboardingService.completeOnboarding(TenantContext.currentCompanyId());
    }

    @PostMapping(value = "/sources", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public KnowledgeSourceResponse uploadSource(@RequestParam("file") MultipartFile file) {
        return knowledgeIngestionService.uploadSource(TenantContext.currentCompanyId(), file);
    }

    @GetMapping("/sources")
    public List<KnowledgeSourceResponse> listSources() {
        return knowledgeIngestionService.listSources(TenantContext.currentCompanyId());
    }
}
