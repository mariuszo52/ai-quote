package com.aiquote.backend.pdf;

import java.io.IOException;
import java.io.InputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

/**
 * Loads the embedded DejaVu Sans font pair (regular/bold) into a given PDDocument.
 * PDFBox's built-in Standard 14 fonts don't cover Polish diacritics (ą ć ę ł ń ó ś ź ż),
 * so a real Unicode TrueType font must be embedded — DejaVu Sans is bundled under
 * src/main/resources/fonts (SIL Open Font License, see LICENSE-DejaVuSans.txt there)
 * specifically for this. A PDFont is bound to the PDDocument it's loaded into, so this
 * must be called once per document, not cached/shared across documents.
 */
public final class PdfFonts {

    private static final String REGULAR_PATH = "/fonts/DejaVuSans.ttf";
    private static final String BOLD_PATH = "/fonts/DejaVuSans-Bold.ttf";

    public final PDFont regular;
    public final PDFont bold;

    private PdfFonts(PDFont regular, PDFont bold) {
        this.regular = regular;
        this.bold = bold;
    }

    public static PdfFonts load(PDDocument document) {
        try {
            return new PdfFonts(loadFont(document, REGULAR_PATH), loadFont(document, BOLD_PATH));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load embedded PDF fonts", e);
        }
    }

    private static PDFont loadFont(PDDocument document, String classpathPath) throws IOException {
        try (InputStream in = PdfFonts.class.getResourceAsStream(classpathPath)) {
            if (in == null) {
                throw new IllegalStateException("Font resource not found on classpath: " + classpathPath);
            }
            return PDType0Font.load(document, in);
        }
    }
}
