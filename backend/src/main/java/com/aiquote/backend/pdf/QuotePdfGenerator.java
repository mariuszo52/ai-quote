package com.aiquote.backend.pdf;

import com.aiquote.backend.company.Company;
import com.aiquote.backend.conversation.Attachment;
import com.aiquote.backend.conversation.AttachmentRepository;
import com.aiquote.backend.conversation.Message;
import com.aiquote.backend.conversation.MessageRepository;
import com.aiquote.backend.conversation.MessageRole;
import com.aiquote.backend.file.StorageService;
import com.aiquote.backend.lead.Lead;
import com.aiquote.backend.quote.Quote;
import com.aiquote.backend.quote.QuoteLineItem;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Renders the OWNER-facing internal PDF: full analysis of a quote including the
 * conversation, photos, AI confidence/reasoning/uncertainFactors and both notes.
 * Generated fresh on every request (never cached) specifically so an owner edit is
 * always reflected — see Quote's "current" fields, which are exactly what an edit
 * updates. This is deliberately a different, more detailed document than
 * OfferPdfGenerator's client-facing PDF (Etap 12), which must never see this class.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QuotePdfGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

    private static final Map<String, String> STATUS_LABELS = Map.of(
            "DRAFT", "Szkic",
            "WAITING_FOR_OWNER", "Oczekuje na akceptację",
            "OWNER_EDITED", "Poprawione przez właściciela",
            "APPROVED", "Zaakceptowane",
            "SENT_TO_CLIENT", "Wysłane do klienta",
            "CANCELLED", "Anulowane");

    private final MessageRepository messageRepository;
    private final AttachmentRepository attachmentRepository;
    private final StorageService storageService;
    private final ObjectMapper objectMapper;

    public byte[] generate(Quote quote, Lead lead, Company company) {
        try (PdfWriter w = new PdfWriter()) {
            float col = w.contentWidth;

            CompanyBrandingRenderer.renderLogoAndName(w, company, storageService);
            w.spacer(4);
            w.line("Wycena robocza #" + quote.getId() + " — status: " + statusLabel(quote.getStatus().name()), w.fonts.regular, 10);
            w.line("Data wygenerowania dokumentu: " + DATE_FORMAT.format(java.time.Instant.now()), w.fonts.regular, 9);
            w.line("Ostatnia aktualizacja wyceny: " + DATE_FORMAT.format(quote.getUpdatedAt()), w.fonts.regular, 9);
            w.spacer(6);
            w.hr(CompanyBrandingRenderer.parseColor(company.getResolvedPrimaryColor()));

            w.line("Dane klienta", w.fonts.bold, 12);
            w.line("Klient: " + lead.getClientName(), w.fonts.regular, 10);
            w.line("Telefon: " + lead.getClientPhone(), w.fonts.regular, 10);
            if (lead.getClientEmail() != null) {
                w.line("Email: " + lead.getClientEmail(), w.fonts.regular, 10);
            }
            w.spacer(10);

            List<Message> messages = messageRepository.findByConversationIdOrderByIdAsc(quote.getConversationId());
            w.line("Opis zlecenia — przebieg rozmowy z klientem", w.fonts.bold, 12);
            w.spacer(2);
            for (Message message : messages) {
                String speaker = message.getRole() == MessageRole.ASSISTANT ? "AI" : "Klient";
                w.paragraph(speaker + ": " + message.getContent(), w.fonts.regular, 9.5f);
                w.spacer(3);
            }
            w.spacer(6);

            List<Attachment> attachments = attachmentRepository.findByConversationIdOrderByIdAsc(quote.getConversationId());
            if (!attachments.isEmpty()) {
                w.line("Zdjęcia od klienta", w.fonts.bold, 12);
                w.spacer(2);
                for (Attachment attachment : attachments) {
                    try (InputStream in = storageService.retrieve(attachment.getStorageKey())) {
                        w.image(in.readAllBytes(), "att-" + attachment.getId(), 180, 180);
                        w.line(attachment.getOriginalFilename(), w.fonts.regular, 8);
                        w.spacer(6);
                    } catch (Exception e) {
                        log.warn("Skipping attachment {} in quote PDF: {}", attachment.getId(), e.getMessage());
                    }
                }
                w.spacer(4);
            }

            w.line("Pozycje wyceny", w.fonts.bold, 12);
            w.spacer(2);
            renderItemsTable(w, readItems(quote.getItemsJson()), quote.getCurrency(), col);
            w.spacer(2);
            w.line("Razem: " + quote.getTotal() + " " + quote.getCurrency(), w.fonts.bold, 13);
            w.spacer(10);

            w.line("Ocena AI", w.fonts.bold, 12);
            w.line("Pewność wyceny: " + confidenceLabel(quote.getAiConfidence()), w.fonts.regular, 10);
            if (quote.getAiReasoning() != null && !quote.getAiReasoning().isBlank()) {
                w.spacer(2);
                w.paragraph(quote.getAiReasoning(), w.fonts.regular, 10);
            }
            List<String> uncertainFactors = readStringList(quote.getUncertainFactorsJson());
            if (!uncertainFactors.isEmpty()) {
                w.spacer(4);
                w.line("Wymaga uwagi:", w.fonts.bold, 10);
                for (String factor : uncertainFactors) {
                    w.bullet(factor, w.fonts.regular, 10);
                }
            }
            w.spacer(10);

            boolean hasInternalNote = quote.getInternalNote() != null && !quote.getInternalNote().isBlank();
            boolean hasClientNote = quote.getClientNote() != null && !quote.getClientNote().isBlank();
            if (hasInternalNote || hasClientNote) {
                w.line("Notatki", w.fonts.bold, 12);
                if (hasInternalNote) {
                    w.line("Notatka wewnętrzna:", w.fonts.bold, 10);
                    w.paragraph(quote.getInternalNote(), w.fonts.regular, 10);
                    w.spacer(4);
                }
                if (hasClientNote) {
                    w.line("Notatka dla klienta:", w.fonts.bold, 10);
                    w.paragraph(quote.getClientNote(), w.fonts.regular, 10);
                }
            }

            return w.toBytes();
        }
    }

    private void renderItemsTable(PdfWriter w, List<QuoteLineItem> items, String currency, float contentWidth) {
        float[] widths = {contentWidth * 0.42f, contentWidth * 0.16f, contentWidth * 0.20f, contentWidth * 0.22f};
        String[] headers = {"Pozycja", "Ilość", "Cena jedn.", "Razem"};
        List<String[]> rows = items.stream()
                .map(item -> new String[] {
                        item.name() + (item.description() != null && !item.description().isBlank() ? "\n" + item.description() : ""),
                        formatQuantity(item),
                        item.unitPrice() != null ? item.unitPrice() + " " + currency : "wymaga wyceny",
                        item.totalPrice() != null ? item.totalPrice() + " " + currency : "—",
                })
                .toList();
        w.table(headers, rows, widths, 9.5f);
    }

    private String formatQuantity(QuoteLineItem item) {
        if (item.quantity() == null) {
            return "—";
        }
        return item.unit() != null ? item.quantity() + " " + item.unit() : String.valueOf(item.quantity());
    }

    private String confidenceLabel(Double confidence) {
        return confidence != null ? Math.round(confidence * 100) + "%" : "nieznana";
    }

    private String statusLabel(String status) {
        return STATUS_LABELS.getOrDefault(status, status);
    }

    private List<QuoteLineItem> readItems(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<QuoteLineItem>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse quote items", e);
        }
    }

    private List<String> readStringList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse quote uncertain factors", e);
        }
    }
}
