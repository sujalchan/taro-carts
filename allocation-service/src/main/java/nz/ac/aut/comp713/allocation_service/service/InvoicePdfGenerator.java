package nz.ac.aut.comp713.allocation_service.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import nz.ac.aut.comp713.allocation_service.exception.InvoiceGenerationException;

@Component
public class InvoicePdfGenerator {
    // Increment when a PDF layout change should refresh previously issued invoices.
    public static final int TEMPLATE_VERSION = 1;
    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    // Match the allocation site's CSS palette in static/css/style.css.
    private static final Color TEXT = new Color(0x24, 0x33, 0x33);
    private static final Color MUTED = new Color(0x52, 0x64, 0x64);
    private static final Color ACCENT = new Color(0x17, 0x66, 0x5f);
    private static final Color PALE = new Color(0xf4, 0xf6, 0xf6);
    private static final Color RULE = new Color(0xd5, 0xde, 0xde);
    private static final String WATERMARK = "EXAMPLE";
    private static final int WATERMARK_SIZE = 140;
    private static final float WATERMARK_OPACITY = 0.1f;
    private static final String EXAMPLE_ADDRESS_LINE_1 = "Address: AUT City Campus";
    private static final String EXAMPLE_ADDRESS_LINE_2 =
            "55 Wellesley Street East, Auckland Central 1010";
    private static final String EXAMPLE_GST_NUMBER = "000000000 (EXAMPLE ONLY)";
    private static final float LEFT = 48;
    private static final float RIGHT = 547;
    private static final float TABLE_WIDTH = RIGHT - LEFT;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final String supplierName;
    private final String supplierAddress;
    private final String supplierGstNumber;

    public InvoicePdfGenerator(
            @Value("${invoice.supplier.name:Taro Carts}") String supplierName,
            @Value("${invoice.supplier.address:}") String supplierAddress,
            @Value("${invoice.supplier.gst-number:}") String supplierGstNumber) {
        this.supplierName = supplierName;
        this.supplierAddress = supplierAddress;
        this.supplierGstNumber = supplierGstNumber;
    }

    public record Line(String productName, int quantity, BigDecimal pricePerKg, BigDecimal amount) {}

    public record Document(String number, LocalDate issuedOn, String customerName,
            String customerPhone, LocalDate weekStart, List<Line> lines,
            BigDecimal subtotal, BigDecimal gst, BigDecimal total) {}

    public byte[] render(Document invoice) {
        try (PDDocument pdf = new PDDocument(); PageWriter page = new PageWriter(pdf, invoice)) {
            page.drawFirstPageHeader();
            page.drawTableHeader();
            for (Line item : invoice.lines()) page.drawItem(item);
            page.drawTotals();
            page.close();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            pdf.save(output);
            return output.toByteArray();
        } catch (IOException | IllegalArgumentException exception) {
            throw new InvoiceGenerationException(exception);
        }
    }

    private static String money(BigDecimal amount) {
        return String.format(Locale.US, "$%,.2f", amount.setScale(2, RoundingMode.HALF_UP));
    }

    private static String printable(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value,
                Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
        return normalized.replaceAll("[^\\x20-\\x7E]", "?");
    }

    private final class PageWriter implements AutoCloseable {
        private final PDDocument pdf;
        private final Document invoice;
        private PDPageContentStream stream;
        private float y;
        private int pageNumber;
        private int rowNumber;

        private PageWriter(PDDocument pdf, Document invoice) throws IOException {
            this.pdf = pdf;
            this.invoice = invoice;
            newPage();
        }

        private void newPage() throws IOException {
            finishPage();
            PDPage page = new PDPage(PDRectangle.A4);
            pdf.addPage(page);
            stream = new PDPageContentStream(pdf, page);
            stream.setNonStrokingColor(Color.WHITE);
            stream.addRect(0, 0, PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
            stream.fill();
            pageNumber++;
            y = 790;
            if (pageNumber > 1) {
                write(LEFT, y, supplierName, 13, BOLD, TEXT);
                right(RIGHT, y, invoice.number(), 10, FONT, MUTED);
                rule(LEFT, y - 14, RIGHT, RULE);
                y -= 34;
            }
        }

        private void drawFirstPageHeader() throws IOException {
            stream.setNonStrokingColor(ACCENT);
            stream.addRect(0, 720, PDRectangle.A4.getWidth(), 122);
            stream.fill();
            write(LEFT, 788, supplierName, 21, BOLD, Color.WHITE);
            write(LEFT, 754, "INVOICE", 26, BOLD, Color.WHITE);
            right(RIGHT, 759, invoice.number(), 12, BOLD, Color.WHITE);

            write(LEFT, 688, "FROM", 9, BOLD, MUTED);
            write(LEFT, 670, supplierName, 11, BOLD, TEXT);
            float supplierY = 652;
            if (supplierAddress.isBlank()) {
                write(LEFT, supplierY, EXAMPLE_ADDRESS_LINE_1, 9, FONT, MUTED);
                supplierY -= 14;
                write(LEFT, supplierY, EXAMPLE_ADDRESS_LINE_2, 9, FONT, MUTED);
                supplierY -= 14;
            } else {
                for (String addressLine : wrap("Address: " + supplierAddress, 270, FONT, 9)) {
                    write(LEFT, supplierY, addressLine, 9, FONT, MUTED);
                    supplierY -= 14;
                }
            }
            String gstNumber = supplierGstNumber.isBlank() ? EXAMPLE_GST_NUMBER : supplierGstNumber;
            write(LEFT, supplierY, "GST number: " + gstNumber, 9, FONT, MUTED);

            write(337, 688, "BILL TO", 9, BOLD, MUTED);
            write(337, 670, invoice.customerName(), 11, BOLD, TEXT);
            if (invoice.customerPhone() != null && !invoice.customerPhone().isBlank()) {
                write(337, 652, "Phone: " + invoice.customerPhone(), 9, FONT, MUTED);
            }

            float detailsRule = Math.min(615, supplierY - 19);
            rule(LEFT, detailsRule, RIGHT, RULE);
            write(LEFT, detailsRule - 22, "ISSUED", 8, BOLD, MUTED);
            write(LEFT, detailsRule - 39, invoice.issuedOn().format(DATE), 10, BOLD, TEXT);
            write(235, detailsRule - 22, "ALLOCATION WEEK", 8, BOLD, MUTED);
            write(235, detailsRule - 39, invoice.weekStart().format(DATE), 10, BOLD, TEXT);
            right(RIGHT, detailsRule - 39, "NZD", 10, BOLD, TEXT);
            y = detailsRule - 65;
        }

        private void drawTableHeader() throws IOException {
            stream.setNonStrokingColor(ACCENT);
            stream.addRect(LEFT, y - 28, TABLE_WIDTH, 28);
            stream.fill();
            write(LEFT + 11, y - 18, "DESCRIPTION", 9, BOLD, Color.WHITE);
            right(351, y - 18, "QTY (KG)", 9, BOLD, Color.WHITE);
            right(446, y - 18, "RATE", 9, BOLD, Color.WHITE);
            right(RIGHT - 9, y - 18, "AMOUNT", 9, BOLD, Color.WHITE);
            y -= 28;
        }

        private void drawItem(Line item) throws IOException {
            List<String> description = wrap(item.productName(), 253, FONT, 9);
            float rowHeight = Math.max(34, description.size() * 13 + 18);
            if (y - rowHeight < 105) {
                newPage();
                drawTableHeader();
            }

            if (rowNumber++ % 2 == 0) {
                stream.setNonStrokingColor(PALE);
                stream.addRect(LEFT, y - rowHeight, TABLE_WIDTH, rowHeight);
                stream.fill();
            }
            float baseline = y - 19;
            for (String line : description) {
                write(LEFT + 11, baseline, line, 9, FONT, TEXT);
                baseline -= 13;
            }
            right(351, y - 19, Integer.toString(item.quantity()), 9, FONT, TEXT);
            right(446, y - 19, money(item.pricePerKg()), 9, FONT, TEXT);
            right(RIGHT - 9, y - 19, money(item.amount()), 9, BOLD, TEXT);
            y -= rowHeight;
            rule(LEFT, y, RIGHT, RULE);
        }

        private void drawTotals() throws IOException {
            if (y < 205) newPage();
            y -= 28;
            write(LEFT, y, "Prices include 15% GST", 9, FONT, MUTED);
            right(452, y, "Subtotal (excl GST)", 9, FONT, MUTED);
            right(RIGHT - 9, y, money(invoice.subtotal()), 10, FONT, TEXT);
            y -= 24;
            right(452, y, "GST (15%)", 9, BOLD, ACCENT);
            right(RIGHT - 9, y, money(invoice.gst()), 10, BOLD, ACCENT);
            y -= 12;
            rule(335, y, RIGHT, ACCENT);
            y -= 25;
            right(452, y, "TOTAL (NZD)", 11, BOLD, ACCENT);
            right(RIGHT - 9, y, money(invoice.total()), 13, BOLD, ACCENT);

        }

        private void drawWatermark() throws IOException {
            PDExtendedGraphicsState transparency = new PDExtendedGraphicsState();
            transparency.setNonStrokingAlphaConstant(WATERMARK_OPACITY);
            float textWidth = BOLD.getStringWidth(WATERMARK) * WATERMARK_SIZE / 1000;
            float textHeight = WATERMARK_SIZE * 0.75f;
            double angle = Math.toRadians(45);
            float x = (float) ((PDRectangle.A4.getWidth()
                    - textWidth * Math.cos(angle) + textHeight * Math.sin(angle)) / 2);
            float y = (float) ((PDRectangle.A4.getHeight()
                    - textWidth * Math.sin(angle) - textHeight * Math.cos(angle)) / 2);

            stream.saveGraphicsState();
            stream.setGraphicsStateParameters(transparency);
            stream.beginText();
            stream.setNonStrokingColor(ACCENT);
            stream.setFont(BOLD, WATERMARK_SIZE);
            stream.setTextMatrix(Matrix.getRotateInstance(angle, x, y));
            stream.showText(WATERMARK);
            stream.endText();
            stream.restoreGraphicsState();
        }

        private List<String> wrap(String value, float maxWidth, PDType1Font font, int size)
                throws IOException {
            String text = printable(value).strip();
            List<String> lines = new ArrayList<>();
            while (!text.isEmpty()) {
                int end = text.length();
                while (end > 1 && font.getStringWidth(text.substring(0, end)) * size / 1000 > maxWidth) end--;
                if (end < text.length()) {
                    int space = text.lastIndexOf(' ', end);
                    if (space > 0) end = space;
                }
                lines.add(text.substring(0, end));
                text = text.substring(end).stripLeading();
            }
            if (lines.isEmpty()) lines.add("");
            return lines;
        }

        private void write(float x, float baseline, String value, int size,
                PDType1Font font, Color color) throws IOException {
            stream.beginText();
            stream.setNonStrokingColor(color);
            stream.setFont(font, size);
            stream.newLineAtOffset(x, baseline);
            stream.showText(printable(value));
            stream.endText();
        }

        private void right(float edge, float baseline, String value, int size,
                PDType1Font font, Color color) throws IOException {
            String text = printable(value);
            float width = font.getStringWidth(text) * size / 1000;
            write(edge - width, baseline, text, size, font, color);
        }

        private void rule(float from, float baseline, float to, Color color) throws IOException {
            stream.setStrokingColor(color);
            stream.setLineWidth(0.7f);
            stream.moveTo(from, baseline);
            stream.lineTo(to, baseline);
            stream.stroke();
        }

        private void finishPage() throws IOException {
            if (stream == null) return;
            rule(LEFT, 60, RIGHT, RULE);
            write(LEFT, 43, supplierName + " | " + invoice.number(), 8, FONT, MUTED);
            right(RIGHT, 43, "Page " + pageNumber, 8, FONT, MUTED);
            drawWatermark();
            stream.close();
            stream = null;
        }

        @Override
        public void close() throws IOException {
            finishPage();
        }
    }
}
