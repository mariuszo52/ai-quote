package com.aiquote.backend.knowledgebase;

import com.aiquote.backend.conversation.Attachment;
import com.aiquote.backend.conversation.AttachmentKind;
import com.aiquote.backend.conversation.AttachmentRepository;
import com.aiquote.backend.file.FileSignature;
import com.aiquote.backend.file.StorageService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class KnowledgeIngestionService {

    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;

    private static final Set<String> PDF_CONTENT_TYPES = Set.of("application/pdf");
    private static final Set<String> EXCEL_CONTENT_TYPES = Set.of(
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final StorageService storageService;
    private final AttachmentRepository attachmentRepository;
    private final KnowledgeSourceRepository knowledgeSourceRepository;
    private final KnowledgeSourceProcessor processor;

    public KnowledgeSourceResponse uploadSource(Long companyId, MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidDocumentException("Plik jest pusty.");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new InvalidDocumentException("Plik jest za duży (limit 10 MB).");
        }

        KnowledgeSourceType type = resolveType(file);

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new InvalidDocumentException("Nie udało się odczytać pliku.");
        }
        // Etap 21: resolveType() above trusts the declared Content-Type/filename, both
        // client-controlled and spoofable — confirm the bytes actually match.
        boolean signatureMatches = type == KnowledgeSourceType.PDF ? FileSignature.isPdf(bytes) : FileSignature.isExcel(bytes);
        if (!signatureMatches) {
            throw new InvalidDocumentException("Zawartość pliku nie odpowiada jego typowi (PDF/Excel).");
        }

        String storageKey = storageService.store(
                companyId, file.getOriginalFilename(), file.getContentType(), new ByteArrayInputStream(bytes), bytes.length);

        Attachment attachment = attachmentRepository.save(new Attachment(
                companyId,
                type == KnowledgeSourceType.PDF ? AttachmentKind.PDF : AttachmentKind.EXCEL,
                storageKey,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize()));

        KnowledgeSource source = knowledgeSourceRepository.save(new KnowledgeSource(companyId, type, attachment.getId()));

        processor.process(source.getId());

        return KnowledgeSourceResponse.from(source, attachment.getOriginalFilename());
    }

    public List<KnowledgeSourceResponse> listSources(Long companyId) {
        return knowledgeSourceRepository.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .map(source -> {
                    String filename = attachmentRepository.findById(source.getAttachmentId())
                            .map(Attachment::getOriginalFilename)
                            .orElse("(nieznany plik)");
                    return KnowledgeSourceResponse.from(source, filename);
                })
                .toList();
    }

    private KnowledgeSourceType resolveType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType != null && PDF_CONTENT_TYPES.contains(contentType)) {
            return KnowledgeSourceType.PDF;
        }
        if (contentType != null && EXCEL_CONTENT_TYPES.contains(contentType)) {
            return KnowledgeSourceType.EXCEL;
        }

        String filename = file.getOriginalFilename();
        if (filename != null) {
            String lower = filename.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".pdf")) {
                return KnowledgeSourceType.PDF;
            }
            if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
                return KnowledgeSourceType.EXCEL;
            }
        }

        throw new InvalidDocumentException("Obsługiwane są tylko pliki PDF oraz Excel (.xlsx, .xls).");
    }
}
