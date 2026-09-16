package com.aiquote.backend.knowledgebase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.aiquote.backend.conversation.AttachmentRepository;
import com.aiquote.backend.file.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

/** Etap 21: uploadSource must validate actual file bytes, not just the client-declared
 * Content-Type/filename that resolveType() reads. */
@ExtendWith(MockitoExtension.class)
class KnowledgeIngestionServiceTest {

    @Mock
    private StorageService storageService;
    @Mock
    private AttachmentRepository attachmentRepository;
    @Mock
    private KnowledgeSourceRepository knowledgeSourceRepository;
    @Mock
    private KnowledgeSourceProcessor processor;

    private KnowledgeIngestionService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeIngestionService(storageService, attachmentRepository, knowledgeSourceRepository, processor);
    }

    @Test
    void rejectsAFileDeclaredAsPdfWhoseBytesArentActuallyPdf() {
        MockMultipartFile file = new MockMultipartFile("file", "cennik.pdf", "application/pdf", "just plain text, not a pdf".getBytes());

        assertThatThrownBy(() -> service.uploadSource(5L, file)).isInstanceOf(InvalidDocumentException.class);
        verify(storageService, never()).store(any(), any(), any(), any(), anyLong());
    }

    @Test
    void rejectsAFileDeclaredAsExcelWhoseBytesArentActuallyExcel() {
        MockMultipartFile file = new MockMultipartFile("file", "cennik.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "just plain text, not an xlsx".getBytes());

        assertThatThrownBy(() -> service.uploadSource(5L, file)).isInstanceOf(InvalidDocumentException.class);
        verify(storageService, never()).store(any(), any(), any(), any(), anyLong());
    }

    @Test
    void acceptsARealPdfSignature() {
        byte[] pdfBytes = "%PDF-1.4\n%%EOF".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "cennik.pdf", "application/pdf", pdfBytes);
        org.mockito.Mockito.when(storageService.store(anyLong(), anyString(), anyString(), any(), anyLong())).thenReturn("key");
        org.mockito.Mockito.when(attachmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.when(knowledgeSourceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.uploadSource(5L, file);

        verify(storageService).store(org.mockito.ArgumentMatchers.eq(5L), anyString(), anyString(), any(), anyLong());
    }
}
