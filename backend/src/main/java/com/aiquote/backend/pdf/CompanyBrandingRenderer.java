package com.aiquote.backend.pdf;

import com.aiquote.backend.company.Company;
import com.aiquote.backend.file.StorageService;
import java.awt.Color;
import java.io.InputStream;
import lombok.extern.slf4j.Slf4j;

/**
 * Shared "branded document header" for both PDF generators (Etap 17) — logo (if the
 * company uploaded one) above the display name, so a QuotePdfGenerator/OfferPdfGenerator
 * change to the header shape only has to happen once. Deliberately just the
 * logo+name; each generator still draws its own subsequent lines (status, date, etc.)
 * and calls PdfWriter#hr(Color) itself with parseColor's result for the accent rule.
 */
@Slf4j
public final class CompanyBrandingRenderer {

    private static final Color FALLBACK_COLOR = new Color(0x4f, 0x46, 0xe5);

    private CompanyBrandingRenderer() {
    }

    public static void renderLogoAndName(PdfWriter w, Company company, StorageService storageService) {
        if (company.hasLogo()) {
            try (InputStream in = storageService.retrieve(company.getLogoStorageKey())) {
                w.image(in.readAllBytes(), "logo-" + company.getId(), 140, 60);
                w.spacer(4);
            } catch (Exception e) {
                log.warn("Skipping logo for company {} in PDF: {}", company.getId(), e.getMessage());
            }
        }
        w.line(company.getDisplayName(), w.fonts.bold, 18);
    }

    public static Color parseColor(String hex) {
        try {
            return Color.decode(hex);
        } catch (Exception e) {
            return FALLBACK_COLOR;
        }
    }
}
