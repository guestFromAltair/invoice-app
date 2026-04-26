package com.invoiceapp.backend.pdf.service;

import com.invoiceapp.backend.invoice.domain.*;
import com.invoiceapp.backend.shared.exception.InvoiceAppException;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PdfGenerationService {

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;

    private static String YOUR_COMPANY_NAME = "InvoiceApp";

    // Colours
    private static final DeviceRgb COLOUR_PRIMARY   = new DeviceRgb(30, 64, 175);
    private static final DeviceRgb COLOUR_LIGHT_BG  = new DeviceRgb(239, 246, 255);
    private static final DeviceRgb COLOUR_TEXT_GREY = new DeviceRgb(107, 114, 128);
    private static final DeviceRgb COLOUR_BORDER    = new DeviceRgb(209, 213, 219);

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private String yourCompanyName;

    @Transactional(readOnly = true)
    public byte[] generateInvoicePdf(UUID invoiceId, UUID userId) {
        Invoice invoice = invoiceRepository
                .findByIdAndCreatedById(invoiceId, userId)
                .orElseThrow(() -> new InvoiceAppException(
                        "Invoice not found", HttpStatus.NOT_FOUND
                ));

        List<Payment> payments = paymentRepository.findAllByInvoiceId(invoiceId);
        try {
            return buildPdf(invoice, payments);
        } catch (IOException e) {
            throw new InvoiceAppException(
                    "Failed to generate PDF", HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    private byte[] buildPdf(Invoice invoice, List<Payment> payments) throws IOException {
        // Generate PDF bytes in memory
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (PdfWriter writer   = new PdfWriter(buffer);
             PdfDocument pdf    = new PdfDocument(writer);
             Document document  = new Document(pdf, PageSize.A4)) {

            document.setMargins(40, 50, 40, 50);

            PdfFont fontRegular = PdfFontFactory.createFont(
                    com.itextpdf.io.font.constants.StandardFonts.HELVETICA
            );
            PdfFont fontBold = PdfFontFactory.createFont(
                    com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD
            );

            addHeader(document, invoice, fontRegular, fontBold);
            addSpacer(document);
            addBillingAndDetailsSection(document, invoice, fontRegular, fontBold);
            addSpacer(document);
            addLineItemsTable(document, invoice, fontRegular, fontBold);
            addSpacer(document);

            if (!payments.isEmpty()) {
                addPaymentHistory(document, payments, invoice, fontRegular, fontBold);
                addSpacer(document);
            }

            if (invoice.getNotes() != null && !invoice.getNotes().isBlank()) {
                addNotes(document, invoice, fontRegular, fontBold);
            }
        }

        return buffer.toByteArray();
    }

    private void addHeader(Document doc, Invoice invoice, PdfFont regular, PdfFont bold) {
        Table header = new Table(UnitValue.createPercentArray(new float[]{60, 40})).useAllAvailableWidth();
        // Left cell: "INVOICE" title

        Cell titleCell = new Cell()
                .add(new Paragraph("INVOICE")
                        .setFont(bold)
                        .setFontSize(28)
                        .setFontColor(COLOUR_PRIMARY))
                .add(new Paragraph(YOUR_COMPANY_NAME)
                        .setFont(regular)
                        .setFontSize(10)
                        .setFontColor(COLOUR_TEXT_GREY)
                        .setMarginTop(4))
                .setBorder(Border.NO_BORDER);

        Cell numberCell = new Cell()
                .add(new Paragraph(invoice.getInvoiceNumber())
                        .setFont(bold)
                        .setFontSize(16)
                        .setFontColor(COLOUR_PRIMARY)
                        .setTextAlignment(TextAlignment.RIGHT))
                .setBorder(Border.NO_BORDER)
                .setTextAlignment(TextAlignment.RIGHT);

        header.addCell(titleCell);
        header.addCell(numberCell);
        doc.add(header);
    }

    private void addSpacer(Document doc) {
        doc.add(new Paragraph("\n").setFontSize(6));
    }

    private void addBillingAndDetailsSection(Document doc, Invoice invoice,
                                             PdfFont regular, PdfFont bold) {
        Table section = new Table(UnitValue.createPercentArray(new float[]{50, 50}))
                .useAllAvailableWidth();

        Cell billTo = new Cell().setBorder(Border.NO_BORDER);
        billTo.add(new Paragraph("BILL TO")
                .setFont(bold).setFontSize(9)
                .setFontColor(COLOUR_TEXT_GREY));
        billTo.add(new Paragraph(invoice.getClient().getName())
                .setFont(bold).setFontSize(12).setMarginTop(4));

        if (invoice.getClient().getEmail() != null) {
            billTo.add(new Paragraph(invoice.getClient().getEmail())
                    .setFont(regular).setFontSize(10)
                    .setFontColor(COLOUR_TEXT_GREY));
        }
        if (invoice.getClient().getAddress() != null) {
            billTo.add(new Paragraph(invoice.getClient().getAddress())
                    .setFont(regular).setFontSize(10)
                    .setFontColor(COLOUR_TEXT_GREY));
        }
        if (invoice.getClient().getVatNumber() != null) {
            billTo.add(new Paragraph("VAT: " + invoice.getClient().getVatNumber())
                    .setFont(regular).setFontSize(10)
                    .setFontColor(COLOUR_TEXT_GREY));
        }

        Cell details = new Cell().setBorder(Border.NO_BORDER);
        details.add(new Paragraph("INVOICE DETAILS")
                .setFont(bold).setFontSize(9)
                .setFontColor(COLOUR_TEXT_GREY)
                .setTextAlignment(TextAlignment.RIGHT));

        details.add(detailRow("Issue date:",
                invoice.getIssueDate().format(DATE_FORMAT), regular));
        details.add(detailRow("Due date:",
                invoice.getDueDate().format(DATE_FORMAT), regular));
        details.add(detailRow("Status:",
                invoice.getStatus().name(), regular));

        section.addCell(billTo);
        section.addCell(details);
        doc.add(section);
    }

    private Paragraph detailRow(String label, String value, PdfFont regular) {
        return new Paragraph(label + "  " + value)
                .setFont(regular).setFontSize(10)
                .setTextAlignment(TextAlignment.RIGHT)
                .setMarginTop(3);
    }

    private void addLineItemsTable(Document doc, Invoice invoice, PdfFont regular, PdfFont bold) {
        float[] columnWidths = {45, 10, 15, 10, 20};
        Table table = new Table(UnitValue.createPercentArray(columnWidths)).useAllAvailableWidth();

        String[] headers = {"DESCRIPTION", "QTY", "UNIT PRICE", "DISC", "TOTAL"};
        TextAlignment[] alignments = {
                TextAlignment.LEFT,
                TextAlignment.CENTER,
                TextAlignment.RIGHT,
                TextAlignment.CENTER,
                TextAlignment.RIGHT
        };

        for (int i = 0; i < headers.length; i++) {
            table.addHeaderCell(headerCell(headers[i], alignments[i], bold));
        }

        boolean alternate = false;
        for (LineItem item : invoice.getLineItems()) {
            DeviceRgb rowBg = alternate ? COLOUR_LIGHT_BG : null;

            table.addCell(dataCell(item.getDescription(),
                    TextAlignment.LEFT, regular, rowBg));
            table.addCell(dataCell(
                    item.getQuantity().setScale(2, RoundingMode.HALF_UP).toString(),
                    TextAlignment.CENTER, regular, rowBg));
            table.addCell(dataCell(
                    formatMoney(item.getUnitPrice()),
                    TextAlignment.RIGHT, regular, rowBg));
            table.addCell(dataCell(
                    formatPercent(item.getDiscountPct()),
                    TextAlignment.CENTER, regular, rowBg));
            table.addCell(dataCell(
                    formatMoney(item.getLineTotal()),
                    TextAlignment.RIGHT, regular, rowBg));

            alternate = !alternate;
        }

        doc.add(table);

        Table totals = new Table(UnitValue.createPercentArray(new float[]{60, 40}))
                .useAllAvailableWidth()
                .setMarginTop(8);

        addTotalRow(totals, "Subtotal",
                formatMoney(invoice.getSubtotal()), regular, false);

        String taxLabel = String.format("Tax (%s%%)",
                invoice.getTaxRate()
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(0, RoundingMode.HALF_UP));
        addTotalRow(totals, taxLabel,
                formatMoney(invoice.getTaxAmount()), regular, false);

        Cell separator = new Cell(1, 2)
                .setBorderTop(new SolidBorder(COLOUR_BORDER, 1))
                .setBorderBottom(Border.NO_BORDER)
                .setBorderLeft(Border.NO_BORDER)
                .setBorderRight(Border.NO_BORDER)
                .add(new Paragraph(""));
        totals.addCell(separator);

        addTotalRow(totals, "TOTAL",
                formatMoney(invoice.getTotal()), bold, true);

        doc.add(totals);
    }

    private void addPaymentHistory(Document doc, List<Payment> payments,
                                   Invoice invoice,
                                   PdfFont regular, PdfFont bold) {
        doc.add(new Paragraph("PAYMENT HISTORY")
                .setFont(bold).setFontSize(10)
                .setFontColor(COLOUR_TEXT_GREY));

        Table table = new Table(UnitValue.createPercentArray(
                new float[]{20, 25, 35, 20}))
                .useAllAvailableWidth();

        // Header
        for (String h : new String[]{"DATE", "METHOD", "NOTES", "AMOUNT"}) {
            table.addHeaderCell(headerCell(h,
                    h.equals("AMOUNT")
                            ? TextAlignment.RIGHT
                            : TextAlignment.LEFT,
                    bold));
        }

        BigDecimal totalPaid = BigDecimal.ZERO;
        for (Payment payment : payments) {
            LocalDate date = payment.getPaidAt()
                    .atZone(java.time.ZoneOffset.UTC)
                    .toLocalDate();

            table.addCell(dataCell(date.format(DATE_FORMAT),
                    TextAlignment.LEFT, regular, null));
            table.addCell(dataCell(
                    payment.getMethod() != null ? payment.getMethod() : "—",
                    TextAlignment.LEFT, regular, null));
            table.addCell(dataCell(
                    payment.getNotes() != null ? payment.getNotes() : "—",
                    TextAlignment.LEFT, regular, null));
            table.addCell(dataCell(formatMoney(payment.getAmount()),
                    TextAlignment.RIGHT, regular, null));

            totalPaid = totalPaid.add(payment.getAmount());
        }

        doc.add(table);

        Table summary = new Table(UnitValue.createPercentArray(new float[]{60, 40}))
                .useAllAvailableWidth()
                .setMarginTop(6);

        addTotalRow(summary, "Amount paid",
                formatMoney(totalPaid), regular, false);

        BigDecimal remaining = invoice.getTotal()
                .subtract(totalPaid)
                .setScale(4, RoundingMode.HALF_UP);

        addTotalRow(summary, "Balance due",
                formatMoney(remaining), bold,
                remaining.compareTo(BigDecimal.ZERO) > 0);

        doc.add(summary);
    }

    private void addNotes(Document doc, Invoice invoice, PdfFont regular, PdfFont bold) {
        doc.add(new Paragraph("NOTES")
                .setFont(bold).setFontSize(10)
                .setFontColor(COLOUR_TEXT_GREY));
        doc.add(new Paragraph(invoice.getNotes())
                .setFont(regular).setFontSize(10)
                .setMarginTop(4));
    }

    private Cell headerCell(String text, TextAlignment alignment, PdfFont bold) {
        return new Cell()
                .add(new Paragraph(text)
                        .setFont(bold)
                        .setFontSize(9)
                        .setFontColor(ColorConstants.WHITE)
                        .setTextAlignment(alignment))
                .setBackgroundColor(COLOUR_PRIMARY)
                .setPadding(6)
                .setBorder(Border.NO_BORDER);
    }

    private Cell dataCell(String text, TextAlignment alignment,
                          PdfFont font, DeviceRgb background) {
        Cell cell = new Cell()
                .add(new Paragraph(text)
                        .setFont(font)
                        .setFontSize(10)
                        .setTextAlignment(alignment))
                .setPadding(5)
                .setBorderTop(Border.NO_BORDER)
                .setBorderLeft(Border.NO_BORDER)
                .setBorderRight(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(COLOUR_BORDER, 0.5f));

        if (background != null) {
            cell.setBackgroundColor(background);
        }
        return cell;
    }

    private void addTotalRow(Table table, String label, String value,
                             PdfFont font, boolean highlight) {
        DeviceRgb bg = highlight ? COLOUR_LIGHT_BG : null;

        Cell labelCell = new Cell()
                .add(new Paragraph(label)
                        .setFont(font).setFontSize(10)
                        .setTextAlignment(TextAlignment.RIGHT))
                .setPadding(4)
                .setBorder(Border.NO_BORDER);

        Cell valueCell = new Cell()
                .add(new Paragraph(value)
                        .setFont(font).setFontSize(10)
                        .setTextAlignment(TextAlignment.RIGHT))
                .setPadding(4)
                .setBorder(Border.NO_BORDER);

        if (bg != null) {
            labelCell.setBackgroundColor(bg);
            valueCell.setBackgroundColor(bg);
        }

        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private String formatMoney(BigDecimal amount) {
        if (amount == null) return "0.00";
        return String.format("%.2f", amount.setScale(2, RoundingMode.HALF_UP));
    }

    private String formatPercent(BigDecimal decimal) {
        if (decimal == null || decimal.compareTo(BigDecimal.ZERO) == 0) return "—";
        return decimal.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP) + "%";
    }
}