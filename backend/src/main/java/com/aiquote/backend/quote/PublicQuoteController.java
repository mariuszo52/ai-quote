package com.aiquote.backend.quote;

import com.aiquote.backend.company.LogoContent;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicQuoteController {

    private static final long SSE_TIMEOUT_MS = 60_000L;

    private final QuoteAgentService quoteAgentService;
    private final QuoteStreamingHandler streamingHandler;

    @GetMapping("/companies/{slug}")
    public CompanyPublicResponse getCompany(@PathVariable String slug) {
        return quoteAgentService.getCompanyPublicInfo(slug);
    }

    @GetMapping("/companies/{slug}/materials")
    public List<String> getCompanyMaterials(@PathVariable String slug) {
        return quoteAgentService.getCompanyMaterialNames(slug);
    }

    @GetMapping("/companies/{slug}/logo")
    public ResponseEntity<byte[]> getCompanyLogo(@PathVariable String slug) {
        LogoContent logo = quoteAgentService.getCompanyLogo(slug);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(logo.contentType()))
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                .body(logo.bytes());
    }

    @PostMapping("/companies/{slug}/conversations")
    public StartConversationResponse startConversation(@PathVariable String slug) {
        return quoteAgentService.startConversation(slug);
    }

    @PostMapping(value = "/conversations/{id}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessage(
            @PathVariable Long id,
            @RequestHeader("X-Conversation-Token") String token,
            @Valid @RequestBody SendQuoteMessageRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        streamingHandler.stream(emitter, id, token, request.content());
        return emitter;
    }

    @PostMapping("/conversations/{id}/submit-contact")
    public LeadCreatedResponse submitContact(
            @PathVariable Long id,
            @RequestHeader("X-Conversation-Token") String token,
            @Valid @RequestBody SubmitContactRequest request) {
        return quoteAgentService.submitContact(id, token, request);
    }

    @PostMapping(value = "/conversations/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AttachmentUploadedResponse uploadAttachment(
            @PathVariable Long id,
            @RequestHeader("X-Conversation-Token") String token,
            @RequestParam("file") MultipartFile file) {
        return quoteAgentService.uploadAttachment(id, token, file);
    }
}
