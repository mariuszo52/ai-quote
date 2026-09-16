package com.aiquote.backend.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Etap 21: upload validation must check the actual file bytes, not just the
 * client-declared Content-Type — these are the checks that back that. */
class FileSignatureTest {

    @Test
    void recognizesRealJpegPngWebpSignatures() {
        assertThat(FileSignature.isJpeg(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0})).isTrue();
        assertThat(FileSignature.isPng(new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A})).isTrue();
        byte[] webp = new byte[12];
        webp[0] = 'R';
        webp[1] = 'I';
        webp[2] = 'F';
        webp[3] = 'F';
        webp[8] = 'W';
        webp[9] = 'E';
        webp[10] = 'B';
        webp[11] = 'P';
        assertThat(FileSignature.isWebp(webp)).isTrue();
    }

    @Test
    void rejectsTextMasqueradingAsAnyImageFormat() {
        byte[] text = "not an image, just text pretending to be one".getBytes(StandardCharsets.UTF_8);
        assertThat(FileSignature.isJpeg(text)).isFalse();
        assertThat(FileSignature.isPng(text)).isFalse();
        assertThat(FileSignature.isWebp(text)).isFalse();
        assertThat(FileSignature.isImage(text, "image/jpeg")).isFalse();
        assertThat(FileSignature.isImage(text, "image/png")).isFalse();
        assertThat(FileSignature.isImage(text, "image/webp")).isFalse();
    }

    @Test
    void isImageDispatchesToTheDeclaredFormatOnly() {
        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        assertThat(FileSignature.isImage(png, "image/png")).isTrue();
        // Real PNG bytes declared as JPEG must still fail — the two formats' signatures differ.
        assertThat(FileSignature.isImage(png, "image/jpeg")).isFalse();
    }

    @Test
    void recognizesPdfAndBothExcelSignatures() {
        assertThat(FileSignature.isPdf("%PDF-1.4\n...".getBytes(StandardCharsets.US_ASCII))).isTrue();
        assertThat(FileSignature.isPdf("not a pdf".getBytes(StandardCharsets.US_ASCII))).isFalse();

        byte[] xlsx = {0x50, 0x4B, 0x03, 0x04}; // zip/OOXML container
        byte[] xls = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1}; // OLE2
        assertThat(FileSignature.isExcel(xlsx)).isTrue();
        assertThat(FileSignature.isExcel(xls)).isTrue();
        assertThat(FileSignature.isExcel("just text".getBytes(StandardCharsets.US_ASCII))).isFalse();
    }

    @Test
    void tooShortByteArraysAreRejectedRatherThanThrowing() {
        assertThat(FileSignature.isPng(new byte[] {1, 2})).isFalse();
        assertThat(FileSignature.isWebp(new byte[] {1, 2})).isFalse();
        assertThat(FileSignature.isPdf(new byte[0])).isFalse();
    }
}
