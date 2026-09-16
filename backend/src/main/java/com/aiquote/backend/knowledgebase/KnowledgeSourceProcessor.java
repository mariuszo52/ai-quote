package com.aiquote.backend.knowledgebase;

import com.aiquote.backend.ai.AiClient;
import com.aiquote.backend.ai.AiMessage;
import com.aiquote.backend.ai.AiTool;
import com.aiquote.backend.ai.AiToolUseBlock;
import com.aiquote.backend.ai.AiTurnResult;
import com.aiquote.backend.conversation.Attachment;
import com.aiquote.backend.conversation.AttachmentRepository;
import com.aiquote.backend.file.StorageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Runs the slow part of document ingestion (text extraction + AI summarization) off
 * the request thread. Lives in its own bean, separate from KnowledgeIngestionService,
 * because @Async only works through the Spring proxy — calling it from a method on
 * the same instance would silently run synchronously.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeSourceProcessor {

    private static final int MAX_EXTRACTION_CHARS = 20_000;

    private final KnowledgeSourceRepository knowledgeSourceRepository;
    private final AttachmentRepository attachmentRepository;
    private final StorageService storageService;
    private final CompanyPricingProfileService profileService;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    @Async
    public void process(Long knowledgeSourceId) {
        KnowledgeSource source = knowledgeSourceRepository.findById(knowledgeSourceId).orElse(null);
        if (source == null) {
            return;
        }

        source.markProcessing();
        knowledgeSourceRepository.save(source);

        try {
            Attachment attachment = attachmentRepository.findById(source.getAttachmentId())
                    .orElseThrow(() -> new IllegalStateException("Attachment not found: " + source.getAttachmentId()));

            String rawText;
            try (InputStream in = storageService.retrieve(attachment.getStorageKey())) {
                rawText = extractText(source.getType(), in);
            }

            PricingProfileData extracted = extractStructuredData(rawText);

            source.markProcessed(PricingProfileFormatter.toPromptText(extracted));
            knowledgeSourceRepository.save(source);

            profileService.mergeAiUpdate(source.getCompanyId(), extracted);
        } catch (Exception e) {
            log.warn("Failed to process knowledge source {}", knowledgeSourceId, e);
            source.markFailed(e.getMessage());
            knowledgeSourceRepository.save(source);
        }
    }

    private String extractText(KnowledgeSourceType type, InputStream in) throws IOException {
        return switch (type) {
            case PDF -> extractPdfText(in);
            case EXCEL -> extractExcelText(in);
            case IMAGE, NOTE -> throw new IllegalStateException("Unsupported source type for extraction: " + type);
        };
    }

    private String extractPdfText(InputStream in) throws IOException {
        try (PDDocument document = Loader.loadPDF(in.readAllBytes())) {
            return new PDFTextStripper().getText(document);
        }
    }

    private String extractExcelText(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        DataFormatter formatter = new DataFormatter();
        try (Workbook workbook = WorkbookFactory.create(in)) {
            for (Sheet sheet : workbook) {
                sb.append("Arkusz: ").append(sheet.getSheetName()).append('\n');
                for (Row row : sheet) {
                    List<String> cells = new ArrayList<>();
                    for (Cell cell : row) {
                        cells.add(formatter.formatCellValue(cell));
                    }
                    sb.append(String.join(" | ", cells)).append('\n');
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * A single tool-forced call: the AI reads the document and reports services/prices
     * directly in the structured shape, which CompanyPricingProfileService.mergeAiUpdate
     * then merges field-by-field — no separate "merge two texts" AI round-trip needed.
     */
    private PricingProfileData extractStructuredData(String rawText) {
        String truncated = rawText.length() > MAX_EXTRACTION_CHARS ? rawText.substring(0, MAX_EXTRACTION_CHARS) : rawText;
        String prompt = """
                Poniżej znajduje się treść dokumentu (cennik / oferta) przesłanego przez firmę usługową. \
                Wyodrębnij z niego usługi i informacje przydatne do ich wyceniania: nazwę usługi, sposób \
                naliczania ceny, jednostkę, cenę bazową, widełki cenowe, minimalną opłatę, czynniki \
                wpływające na cenę oraz czy usługa wymaga indywidualnej wyceny. Zapisuj wartości liczbowe \
                TYLKO jeśli są podane wprost w dokumencie — niczego nie zgaduj. Zignoruj elementy \
                nieistotne dla wyceny (np. dane kontaktowe, stopki). Wywołaj narzędzie update_pricing_profile \
                z wynikiem.

                Treść dokumentu:
                %s
                """.formatted(truncated);

        AiTool tool = PricingProfileTool.definition("Zapisuje usługi i ceny wyodrębnione z dokumentu.", objectMapper);
        AiTurnResult result = aiClient.sendMessage(
                "Jesteś asystentem, który wyodrębnia informacje o cenach z dokumentów firmowych.",
                List.of(AiMessage.userText(prompt)),
                List.of(tool));

        for (AiToolUseBlock toolUse : result.extractToolUses()) {
            if (PricingProfileTool.NAME.equals(toolUse.name())) {
                try {
                    return objectMapper.treeToValue(toolUse.input(), PricingProfileData.class);
                } catch (JsonProcessingException e) {
                    throw new IllegalStateException("Invalid update_pricing_profile tool input", e);
                }
            }
        }
        return PricingProfileData.empty();
    }
}
