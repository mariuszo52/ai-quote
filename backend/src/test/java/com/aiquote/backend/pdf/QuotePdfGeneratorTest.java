package com.aiquote.backend.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.aiquote.backend.company.Company;
import com.aiquote.backend.company.CompanyStatus;
import com.aiquote.backend.conversation.Attachment;
import com.aiquote.backend.conversation.AttachmentRepository;
import com.aiquote.backend.conversation.Message;
import com.aiquote.backend.conversation.MessageRepository;
import com.aiquote.backend.conversation.MessageRole;
import com.aiquote.backend.file.StorageService;
import com.aiquote.backend.lead.Lead;
import com.aiquote.backend.quote.Quote;
import com.aiquote.backend.quote.QuoteLineItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Renders real PDFs and reads the text back out via PDFTextStripper to check both
 * structural validity (PDFBox can reload what it wrote) and correct content — in
 * particular Polish diacritics, which is the whole reason the DejaVu font is embedded
 * instead of using a Standard 14 font.
 */
@ExtendWith(MockitoExtension.class)
class QuotePdfGeneratorTest {

    @Mock
    private MessageRepository messageRepository;
    @Mock
    private AttachmentRepository attachmentRepository;
    @Mock
    private StorageService storageService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private QuotePdfGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new QuotePdfGenerator(messageRepository, attachmentRepository, storageService, objectMapper);
    }

    @Test
    void generatesReadablePdfWithPolishCharactersAndItemsTable() throws Exception {
        Company company = new Company("Złota Rączka Sp. z o.o.", "zlota-raczka", CompanyStatus.ACTIVE);
        Lead lead = new Lead(5L, 3L, "Żaneta Wąsik", "600100200", "zaneta@example.com", null, null, null, "PLN", null);
        Quote quote = quote(List.of(item("Malowanie ścian", 20.0, "m2", 15.0)));
        ReflectionTestUtils.setField(quote, "internalNote", "Klient życzy sobie wykończenie w kolorze łososiowym.");
        ReflectionTestUtils.setField(quote, "clientNote", "Dziękujemy za zaufanie.");

        when(messageRepository.findByConversationIdOrderByIdAsc(anyLong())).thenReturn(List.of(
                new Message(2L, MessageRole.USER, "Proszę o wycenę malowania ścian w pokoju."),
                new Message(2L, MessageRole.ASSISTANT, "Jasne, ile metrów kwadratowych ma pomieszczenie?")));
        when(attachmentRepository.findByConversationIdOrderByIdAsc(anyLong())).thenReturn(List.of());

        byte[] pdf = generator.generate(quote, lead, company);
        String text = extractText(pdf);

        assertThat(text).contains("Złota Rączka Sp. z o.o.");
        assertThat(text).contains("Żaneta Wąsik");
        assertThat(text).contains("Malowanie ścian");
        assertThat(text).contains("300.0 PLN"); // 20 * 15
        assertThat(text).contains("łososiowym");
        assertThat(text).contains("Dziękujemy za zaufanie.");
    }

    @Test
    void skipsAttachmentsThatFailToLoadInsteadOfFailingTheWholePdf() {
        Company company = new Company("Testowa", "testowa", CompanyStatus.ACTIVE);
        Lead lead = new Lead(5L, 3L, "Jan Kowalski", "600100200", null, null, null, null, "PLN", null);
        Quote quote = quote(List.of(item("Usługa", 1.0, "szt", 100.0)));

        when(messageRepository.findByConversationIdOrderByIdAsc(anyLong())).thenReturn(List.of());
        Attachment broken = new Attachment(5L, 3L, com.aiquote.backend.conversation.AttachmentKind.IMAGE, "missing-key", "zdjecie.jpg", "image/jpeg", 100L);
        when(attachmentRepository.findByConversationIdOrderByIdAsc(anyLong())).thenReturn(List.of(broken));
        when(storageService.retrieve("missing-key")).thenThrow(new RuntimeException("not found"));

        byte[] pdf = generator.generate(quote, lead, company);

        assertThat(pdf).isNotEmpty();
    }

    @Test
    void handlesManyLineItemsAcrossMultiplePages() throws Exception {
        Company company = new Company("Testowa", "testowa", CompanyStatus.ACTIVE);
        Lead lead = new Lead(5L, 3L, "Jan Kowalski", "600100200", null, null, null, null, "PLN", null);
        List<QuoteLineItem> items = new ArrayList<>();
        for (int i = 1; i <= 60; i++) {
            items.add(item("Pozycja robocza numer " + i, 1.0, "szt", 10.0 * i));
        }
        Quote quote = quote(items);

        when(messageRepository.findByConversationIdOrderByIdAsc(anyLong())).thenReturn(List.of());
        when(attachmentRepository.findByConversationIdOrderByIdAsc(anyLong())).thenReturn(List.of());

        byte[] pdf = generator.generate(quote, lead, company);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
        }
        String text = extractText(pdf);
        assertThat(text).contains("Pozycja robocza numer 1");
        assertThat(text).contains("Pozycja robocza numer 60");
    }

    private String extractText(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private QuoteLineItem item(String name, double quantity, String unit, double unitPrice) {
        return new QuoteLineItem(name, null, quantity, unit, unitPrice, quantity * unitPrice, "pricing_profile");
    }

    private Quote quote(List<QuoteLineItem> items) {
        try {
            String itemsJson = objectMapper.writeValueAsString(items);
            double total = items.stream().mapToDouble(QuoteLineItem::totalPrice).sum();
            Quote quote = new Quote(5L, 3L, 2L, "PLN", itemsJson, total, total, 0.8, "Wycena o dużej pewności.", "[\"Brak dostępu do piwnicy\"]");
            ReflectionTestUtils.setField(quote, "id", 1L);
            return quote;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
