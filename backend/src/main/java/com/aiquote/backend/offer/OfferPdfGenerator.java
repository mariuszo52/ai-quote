package com.aiquote.backend.offer;

import com.aiquote.backend.company.Company;
import com.aiquote.backend.file.StorageService;
import com.aiquote.backend.pdf.CompanyBrandingRenderer;
import com.aiquote.backend.pdf.PdfWriter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Renders the CLIENT-facing final offer PDF — a normal, professional company document.
 * Deliberately only ever takes an Offer (never a Quote): Offer already excludes every
 * AI/internal field (see Offer's javadoc), so there is no field on the type this class
 * could even accidentally render that would leak internal data. Contrast with
 * QuotePdfGenerator (Etap 11), which is the internal document and must never be reused
 * here.
 */
@Component
@RequiredArgsConstructor
public class OfferPdfGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DATE_ONLY_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final ObjectMapper objectMapper;
    private final StorageService storageService;

    public byte[] generate(Offer offer, Company company, String contactEmail) {
        try (PdfWriter w = new PdfWriter()) {
            float col = w.contentWidth;

            CompanyBrandingRenderer.renderLogoAndName(w, company, storageService);
            w.spacer(4);
            w.line("Oferta nr " + offer.getId(), w.fonts.regular, 11);
            w.line("Data: " + DATE_FORMAT.format(offer.getCreatedAt()), w.fonts.regular, 9);
            w.spacer(6);
            w.hr(CompanyBrandingRenderer.parseColor(company.getResolvedPrimaryColor()));

            w.line("Dane klienta", w.fonts.bold, 12);
            w.line("Klient: " + offer.getClientName(), w.fonts.regular, 10);
            w.line("Telefon: " + offer.getClientPhone(), w.fonts.regular, 10);
            if (offer.getClientEmail() != null) {
                w.line("Email: " + offer.getClientEmail(), w.fonts.regular, 10);
            }
            w.spacer(10);

            if (offer.getJobDescription() != null && !offer.getJobDescription().isBlank()) {
                w.line("Opis zlecenia", w.fonts.bold, 12);
                w.spacer(2);
                w.paragraph(offer.getJobDescription(), w.fonts.regular, 10);
                w.spacer(8);
            }

            w.line("Zakres prac i wycena", w.fonts.bold, 12);
            w.spacer(2);
            renderItemsTable(w, readItems(offer.getItemsJson()), offer.getCurrency(), col);
            w.spacer(2);
            w.line("Razem: " + offer.getTotal() + " " + offer.getCurrency(), w.fonts.bold, 13);
            w.spacer(10);

            boolean hasTimeline = offer.getEstimatedTimeline() != null && !offer.getEstimatedTimeline().isBlank();
            boolean hasValidity = offer.getValidUntil() != null;
            if (hasTimeline || hasValidity) {
                w.line("Szczegóły oferty", w.fonts.bold, 12);
                if (hasTimeline) {
                    w.line("Przewidywany termin realizacji: " + offer.getEstimatedTimeline(), w.fonts.regular, 10);
                }
                if (hasValidity) {
                    w.line("Oferta ważna do: " + DATE_ONLY_FORMAT.format(offer.getValidUntil()), w.fonts.regular, 10);
                }
                w.spacer(10);
            }

            w.line("Kontakt", w.fonts.bold, 12);
            boolean hasAnyContact = contactEmail != null || company.getContactPhone() != null || company.getAddress() != null;
            if (!hasAnyContact) {
                w.line("brak danych kontaktowych", w.fonts.regular, 10);
            }
            if (contactEmail != null) {
                w.line(contactEmail, w.fonts.regular, 10);
            }
            if (company.getContactPhone() != null) {
                w.line(company.getContactPhone(), w.fonts.regular, 10);
            }
            if (company.getAddress() != null) {
                w.paragraph(company.getAddress(), w.fonts.regular, 10);
            }

            return w.toBytes();
        }
    }

    private void renderItemsTable(PdfWriter w, List<OfferLineItem> items, String currency, float contentWidth) {
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

    private String formatQuantity(OfferLineItem item) {
        if (item.quantity() == null) {
            return "—";
        }
        return item.unit() != null ? item.quantity() + " " + item.unit() : String.valueOf(item.quantity());
    }

    private List<OfferLineItem> readItems(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<OfferLineItem>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse offer items", e);
        }
    }
}
