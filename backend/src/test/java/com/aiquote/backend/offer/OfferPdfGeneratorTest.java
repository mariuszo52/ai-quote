package com.aiquote.backend.offer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiquote.backend.company.Company;
import com.aiquote.backend.company.CompanyStatus;
import com.aiquote.backend.file.StorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Renders real PDFs and reads the text back out, same approach as
 * QuotePdfGeneratorTest — verifies both structural validity and correct content,
 * especially Polish diacritics. Unlike that test, there is no separate "must not
 * contain AI data" assertion needed here: Offer has no AI fields to render in the
 * first place (see OfferSecurityTest for the structural guarantee).
 */
class OfferPdfGeneratorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StorageService storageService = mock(StorageService.class);
    private final OfferPdfGenerator generator = new OfferPdfGenerator(objectMapper, storageService);

    @Test
    void generatesReadablePdfWithPolishCharactersAndOfferDetails() throws Exception {
        Company company = new Company("Złota Rączka Sp. z o.o.", "zlota-raczka", CompanyStatus.ACTIVE);
        Offer offer = offer(
                List.of(new OfferLineItem("Malowanie ścian", "Dwie warstwy farby lateksowej", 20.0, "m2", 15.0, 300.0)),
                "Żaneta Wąsik",
                "Malowanie salonu w mieszkaniu klientki.",
                "2-3 tygodnie",
                LocalDate.of(2026, 12, 31));

        byte[] pdf = generator.generate(offer, company, "kontakt@zlota-raczka.pl");
        String text = extractText(pdf);

        assertThat(text).contains("Złota Rączka Sp. z o.o.");
        assertThat(text).contains("Żaneta Wąsik");
        assertThat(text).contains("Malowanie ścian");
        assertThat(text).contains("300.0 PLN");
        assertThat(text).contains("2-3 tygodnie");
        assertThat(text).contains("31.12.2026");
        assertThat(text).contains("kontakt@zlota-raczka.pl");
    }

    @Test
    void handlesMissingContactEmailGracefully() throws Exception {
        Company company = new Company("Testowa", "testowa", CompanyStatus.ACTIVE);
        Offer offer = offer(List.of(new OfferLineItem("Usługa", null, 1.0, "szt", 100.0, 100.0)), "Jan Kowalski", null, null, null);

        byte[] pdf = generator.generate(offer, company, null);
        String text = extractText(pdf);

        assertThat(text).contains("brak danych kontaktowych");
    }

    @Test
    void handlesManyLineItemsAcrossMultiplePages() throws Exception {
        Company company = new Company("Testowa", "testowa", CompanyStatus.ACTIVE);
        List<OfferLineItem> items = new ArrayList<>();
        for (int i = 1; i <= 60; i++) {
            items.add(new OfferLineItem("Pozycja robocza numer " + i, null, 1.0, "szt", 10.0 * i, 10.0 * i));
        }
        Offer offer = offer(items, "Jan Kowalski", null, null, null);

        byte[] pdf = generator.generate(offer, company, "a@b.pl");

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
        }
        String text = extractText(pdf);
        assertThat(text).contains("Pozycja robocza numer 1");
        assertThat(text).contains("Pozycja robocza numer 60");
    }

    @Test
    void handlesLongJobDescription() throws Exception {
        Company company = new Company("Testowa", "testowa", CompanyStatus.ACTIVE);
        String longDescription = "Bardzo długi opis zlecenia. ".repeat(40);
        Offer offer = offer(List.of(new OfferLineItem("Usługa", null, 1.0, "szt", 100.0, 100.0)), "Jan Kowalski", longDescription, null, null);

        byte[] pdf = generator.generate(offer, company, "a@b.pl");
        String text = extractText(pdf);

        assertThat(text).contains("Bardzo długi opis zlecenia.");
    }

    @Test
    void usesDisplayNameWhenSet() throws Exception {
        Company company = new Company("Legal Name Sp. z o.o.", "legal-name", CompanyStatus.ACTIVE);
        ReflectionTestUtils.setField(company, "displayName", "Marka Klienta");
        Offer offer = offer(List.of(new OfferLineItem("Usługa", null, 1.0, "szt", 100.0, 100.0)), "Jan Kowalski", null, null, null);

        byte[] pdf = generator.generate(offer, company, "a@b.pl");
        String text = extractText(pdf);

        assertThat(text).contains("Marka Klienta");
        assertThat(text).doesNotContain("Legal Name Sp. z o.o.");
    }

    @Test
    void embedsTheCompanyLogoWhenOneIsSet() throws Exception {
        Company company = new Company("Testowa", "testowa", CompanyStatus.ACTIVE);
        ReflectionTestUtils.setField(company, "logoStorageKey", "logos/1.png");
        ReflectionTestUtils.setField(company, "logoContentType", "image/png");
        when(storageService.retrieve("logos/1.png")).thenReturn(new ByteArrayInputStream(pngBytes()));
        Offer offer = offer(List.of(new OfferLineItem("Usługa", null, 1.0, "szt", 100.0, 100.0)), "Jan Kowalski", null, null, null);

        byte[] pdf = generator.generate(offer, company, "a@b.pl");

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getPage(0).getResources().getXObjectNames()).isNotEmpty();
        }
    }

    @Test
    void showsBrandingContactPhoneAndAddressInKontaktSection() throws Exception {
        Company company = new Company("Testowa", "testowa", CompanyStatus.ACTIVE);
        ReflectionTestUtils.setField(company, "contactPhone", "600100200");
        ReflectionTestUtils.setField(company, "address", "ul. Testowa 1, 00-001 Warszawa");
        Offer offer = offer(List.of(new OfferLineItem("Usługa", null, 1.0, "szt", 100.0, 100.0)), "Jan Kowalski", null, null, null);

        byte[] pdf = generator.generate(offer, company, "a@b.pl");
        String text = extractText(pdf);

        assertThat(text).contains("600100200");
        assertThat(text).contains("ul. Testowa 1");
    }

    private byte[] pngBytes() throws Exception {
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private String extractText(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private Offer offer(List<OfferLineItem> items, String clientName, String jobDescription, String timeline, LocalDate validUntil) {
        try {
            double total = items.stream().mapToDouble(OfferLineItem::totalPrice).sum();
            String itemsJson = objectMapper.writeValueAsString(items);
            Offer offer = new Offer(
                    5L, 1L, "tok-abc", "PLN", itemsJson, total, clientName, "600100200", "klient@example.com",
                    jobDescription, timeline, validUntil, "offers/1.pdf");
            ReflectionTestUtils.setField(offer, "id", 1L);
            ReflectionTestUtils.setField(offer, "createdAt", Instant.now());
            return offer;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
