package com.aiquote.backend.lead;

import com.aiquote.backend.tenant.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The list endpoint (GET /api/leads, bare — no path suffix) deliberately lives on
 * quote.LeadOverviewController instead of here: the leads panel needs each lead's
 * quote status/total joined in, and quote is allowed to depend on lead (never the
 * reverse) — see that controller's javadoc. This class keeps everything that's
 * genuinely lead-only: single-lead detail, status transitions, attachments.
 */
@RestController
@RequestMapping("/api/leads")
@RequiredArgsConstructor
public class LeadController {

    private final LeadService leadService;

    @GetMapping("/{id}")
    public LeadDetailResponse detail(@PathVariable Long id) {
        return leadService.getDetail(TenantContext.currentCompanyId(), id);
    }

    @PatchMapping("/{id}")
    public LeadStatusResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateLeadStatusRequest request) {
        return leadService.updateStatus(TenantContext.currentCompanyId(), id, request.status());
    }

    @GetMapping("/{id}/attachments/{attachmentId}")
    public ResponseEntity<byte[]> getAttachment(@PathVariable Long id, @PathVariable Long attachmentId) {
        LeadAttachmentContent content = leadService.getAttachmentContent(TenantContext.currentCompanyId(), id, attachmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + sanitizeFilename(content.filename()) + "\"")
                .body(content.bytes());
    }

    /** Uploaded filenames are user-controlled and stored verbatim — strip quotes/control chars before echoing into a header. */
    private String sanitizeFilename(String filename) {
        if (filename == null) {
            return "attachment";
        }
        return filename.replaceAll("[\\p{Cntrl}\"]", "_");
    }
}
