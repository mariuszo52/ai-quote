package com.aiquote.backend.pdf;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 * Small hand-rolled layout helper on top of PDFBox's low-level drawing API — PDFBox has
 * no built-in concept of flowing text, tables, or page breaks, so this provides just
 * enough of that (text wrapping, a single-purpose bordered table, image scaling, and
 * automatic page breaks via ensureSpace) to lay out the quote/offer PDFs without
 * pulling in a whole separate reporting library.
 *
 * Not thread-safe; one instance per document being built.
 */
public class PdfWriter implements AutoCloseable {

    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float MARGIN_X = 40f;
    private static final float MARGIN_TOP = 40f;
    private static final float MARGIN_BOTTOM = 50f;

    public final float contentWidth = PAGE_WIDTH - 2 * MARGIN_X;

    private final PDDocument document;
    private PDPageContentStream stream;
    private float cursorY;

    public final PdfFonts fonts;

    public PdfWriter() {
        this.document = new PDDocument();
        this.fonts = PdfFonts.load(document);
        newPage();
    }

    public float cursorY() {
        return cursorY;
    }

    public void newPage() {
        try {
            if (stream != null) {
                stream.close();
            }
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            cursorY = PAGE_HEIGHT - MARGIN_TOP;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start a new PDF page", e);
        }
    }

    /** Starts a new page if the next {@code height} points wouldn't fit above the bottom margin. */
    public void ensureSpace(float height) {
        if (cursorY - height < MARGIN_BOTTOM) {
            newPage();
        }
    }

    public void moveDown(float amount) {
        cursorY -= amount;
    }

    public void line(String text, PDFont font, float size, Color color) {
        float lineHeight = size * 1.3f;
        ensureSpace(lineHeight);
        drawLine(sanitize(text, font), font, size, MARGIN_X, color, cursorY - size);
        cursorY -= lineHeight;
    }

    public void line(String text, PDFont font, float size) {
        line(text, font, size, Color.BLACK);
    }

    /** Wraps on explicit newlines first, then greedily word-wraps each paragraph to maxWidth. */
    public List<String> wrap(String text, PDFont font, float size, float maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return lines;
        }
        text = sanitize(text, font);
        for (String paragraph : text.split("\n", -1)) {
            if (paragraph.isBlank()) {
                lines.add("");
                continue;
            }
            StringBuilder current = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (width(candidate, font, size) > maxWidth && !current.isEmpty()) {
                    lines.add(current.toString());
                    current = new StringBuilder(word);
                } else {
                    current = new StringBuilder(candidate);
                }
            }
            lines.add(current.toString());
        }
        return lines;
    }

    /** Wraps and draws a block of text at full content width, one line at a time (page-breaks as needed). */
    public void paragraph(String text, PDFont font, float size, Color color) {
        float lineHeight = size * 1.35f;
        for (String line : wrap(text, font, size, contentWidth)) {
            ensureSpace(lineHeight);
            if (!line.isEmpty()) {
                drawLine(line, font, size, MARGIN_X, color, cursorY - size);
            }
            cursorY -= lineHeight;
        }
    }

    public void paragraph(String text, PDFont font, float size) {
        paragraph(text, font, size, Color.BLACK);
    }

    public void bullet(String text, PDFont font, float size) {
        float indent = 14f;
        float lineHeight = size * 1.35f;
        List<String> lines = wrap(text, font, size, contentWidth - indent);
        for (int i = 0; i < lines.size(); i++) {
            ensureSpace(lineHeight);
            String prefix = i == 0 ? "• " : "  ";
            drawLine(prefix + lines.get(i), font, size, MARGIN_X, Color.BLACK, cursorY - size);
            cursorY -= lineHeight;
        }
    }

    public void spacer(float amount) {
        cursorY -= amount;
    }

    public void hr() {
        hr(Color.LIGHT_GRAY);
    }

    /** Same rule, in a caller-chosen color — used for the branded accent line under a
     * company's header (Etap 17), falling back to the plain gray rule everywhere else. */
    public void hr(Color color) {
        ensureSpace(10f);
        try {
            stream.setStrokingColor(color);
            stream.moveTo(MARGIN_X, cursorY);
            stream.lineTo(MARGIN_X + contentWidth, cursorY);
            stream.stroke();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to draw separator", e);
        }
        cursorY -= 10f;
    }

    /** Scales the image to fit within maxWidth/maxHeight (never upscaling) and draws it left-aligned. */
    public void image(byte[] imageBytes, String name, float maxWidth, float maxHeight) {
        try {
            PDImageXObject image = PDImageXObject.createFromByteArray(document, imageBytes, name);
            float scale = Math.min(1f, Math.min(maxWidth / image.getWidth(), maxHeight / image.getHeight()));
            float w = image.getWidth() * scale;
            float h = image.getHeight() * scale;
            ensureSpace(h);
            stream.drawImage(image, MARGIN_X, cursorY - h, w, h);
            cursorY -= h + 6f;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to embed image '" + name + "' — skipping it", e);
        }
    }

    /**
     * A single-purpose bordered table: header row (bold) + data rows, each cell wrapped
     * independently to its column width, row height driven by the tallest cell, borders
     * drawn per row so a table can span multiple pages without any row being cut in half.
     */
    public void table(String[] headers, List<String[]> rows, float[] columnWidths, float fontSize) {
        float cellPadding = 4f;
        float lineHeight = fontSize * 1.3f;

        drawTableRow(headers, columnWidths, fonts.bold, fontSize, lineHeight, cellPadding, true);
        for (String[] row : rows) {
            drawTableRow(row, columnWidths, fonts.regular, fontSize, lineHeight, cellPadding, false);
        }
    }

    private void drawTableRow(String[] cells, float[] columnWidths, PDFont font, float fontSize, float lineHeight, float cellPadding, boolean header) {
        List<List<String>> wrappedCells = new ArrayList<>();
        int maxLines = 1;
        for (int i = 0; i < cells.length; i++) {
            List<String> wrapped = wrap(cells[i], font, fontSize, columnWidths[i] - 2 * cellPadding);
            if (wrapped.isEmpty()) {
                wrapped = List.of("");
            }
            wrappedCells.add(wrapped);
            maxLines = Math.max(maxLines, wrapped.size());
        }

        float rowHeight = maxLines * lineHeight + 2 * cellPadding;
        ensureSpace(rowHeight);

        float rowTop = cursorY;
        float rowBottom = cursorY - rowHeight;

        if (header) {
            try {
                stream.setNonStrokingColor(new Color(0xF1, 0xF5, 0xF9));
                stream.addRect(MARGIN_X, rowBottom, sum(columnWidths), rowHeight);
                stream.fill();
            } catch (IOException e) {
                throw new IllegalStateException("Failed to draw table header background", e);
            }
        }

        float x = MARGIN_X;
        for (int i = 0; i < cells.length; i++) {
            float textY = rowTop - cellPadding - fontSize;
            for (String line : wrappedCells.get(i)) {
                drawLine(line, font, fontSize, x + cellPadding, Color.BLACK, textY);
                textY -= lineHeight;
            }
            x += columnWidths[i];
        }

        try {
            stream.setStrokingColor(Color.LIGHT_GRAY);
            stream.addRect(MARGIN_X, rowBottom, sum(columnWidths), rowHeight);
            stream.stroke();
            float colX = MARGIN_X;
            for (int i = 0; i < columnWidths.length - 1; i++) {
                colX += columnWidths[i];
                stream.moveTo(colX, rowTop);
                stream.lineTo(colX, rowBottom);
                stream.stroke();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to draw table borders", e);
        }

        cursorY = rowBottom;
    }

    private float sum(float[] values) {
        float total = 0;
        for (float v : values) {
            total += v;
        }
        return total;
    }

    /**
     * PDFBox throws (IllegalArgumentException, "No glyph for U+XXXX...") from both
     * getStringWidth and showText when asked to measure/draw a character the embedded
     * DejaVu Sans font has no glyph for — AI-generated text occasionally includes emoji,
     * which would otherwise crash the whole PDF. Drop just those characters instead.
     * All text flows through here before being measured (wrap) or drawn (line), so
     * table()/paragraph()/bullet() — which all go through wrap() — are covered too.
     */
    private String sanitize(String text, PDFont font) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        try {
            font.getStringWidth(text);
            return text;
        } catch (Exception e) {
            StringBuilder result = new StringBuilder(text.length());
            text.codePoints().forEach(codePoint -> {
                String ch = new String(Character.toChars(codePoint));
                try {
                    font.getStringWidth(ch);
                    result.append(ch);
                } catch (Exception ignored) {
                    // no glyph for this character in the embedded font — drop it
                }
            });
            return result.toString();
        }
    }

    private float width(String text, PDFont font, float size) {
        try {
            return font.getStringWidth(text) / 1000f * size;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to measure text width for: " + text, e);
        }
    }

    private void drawLine(String text, PDFont font, float size, float x, Color color, float y) {
        try {
            stream.beginText();
            stream.setNonStrokingColor(color);
            stream.setFont(font, size);
            stream.newLineAtOffset(x, y);
            stream.showText(text);
            stream.endText();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to draw text: " + text, e);
        }
    }

    public byte[] toBytes() {
        try {
            if (stream != null) {
                stream.close();
                stream = null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to render PDF bytes", e);
        }
    }

    @Override
    public void close() {
        try {
            document.close();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to close PDF document", e);
        }
    }
}
