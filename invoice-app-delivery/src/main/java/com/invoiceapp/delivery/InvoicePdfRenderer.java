package com.invoiceapp.delivery;

import com.invoiceapp.delivery.event.InvoiceReadyForDeliveryEvent;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

import static com.itextpdf.io.font.constants.StandardFonts.HELVETICA;
import static com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD;

@Component
public class InvoicePdfRenderer {

    private static final String COMPANY = "InvoiceApp";
    private static final DeviceRgb PRIMARY = new DeviceRgb(30, 64, 175);
    private static final DeviceRgb GREY = new DeviceRgb(107, 114, 128);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");

    public byte[] render(InvoiceReadyForDeliveryEvent e) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        try (PdfWriter writer = new PdfWriter(buffer);
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf, PageSize.A4)) {

            doc.setMargins(40, 50, 40, 50);
            PdfFont regular = PdfFontFactory.createFont(HELVETICA);
            PdfFont bold = PdfFontFactory.createFont(HELVETICA_BOLD);

            addHeader(doc, e, regular, bold);
            addRecipient(doc, e, regular, bold);
            addLineItems(doc, e, regular, bold);
            addTotals(doc, e, regular, bold);

            if (e.notes() != null && !e.notes().isBlank()) {
                doc.add(new Paragraph("Notes").setFont(bold).setFontSize(9).setFontColor(GREY).setMarginTop(20));
                doc.add(new Paragraph(e.notes()).setFont(regular).setFontSize(10));
            }
        }
        return buffer.toByteArray();
    }

    private void addHeader(Document doc, InvoiceReadyForDeliveryEvent e, PdfFont regular, PdfFont bold) {
        Table t = new Table(UnitValue.createPercentArray(new float[]{60, 40})).useAllAvailableWidth();
        t.addCell(new Cell().setBorder(Border.NO_BORDER)
                .add(new Paragraph("INVOICE").setFont(bold).setFontSize(28).setFontColor(PRIMARY))
                .add(new Paragraph(COMPANY).setFont(regular).setFontSize(10).setFontColor(GREY)));
        t.addCell(new Cell().setBorder(Border.NO_BORDER)
                .add(new Paragraph(e.invoiceNumber()).setFont(bold).setFontSize(16)
                        .setFontColor(PRIMARY).setTextAlignment(TextAlignment.RIGHT)));
        doc.add(t);
    }

    private void addRecipient(Document doc, InvoiceReadyForDeliveryEvent e, PdfFont regular, PdfFont bold) {
        var r = e.recipient();
        Table t = new Table(UnitValue.createPercentArray(new float[]{50, 50}))
                .useAllAvailableWidth().setMarginTop(24);

        Cell to = new Cell().setBorder(Border.NO_BORDER);
        to.add(new Paragraph("BILL TO").setFont(bold).setFontSize(9).setFontColor(GREY));
        to.add(new Paragraph(r.name()).setFont(bold).setFontSize(12));
        if (r.email() != null) to.add(line(r.email(), regular));
        if (r.address() != null) to.add(line(r.address(), regular));
        if (r.vatNumber() != null) to.add(line("VAT: " + r.vatNumber(), regular));

        Cell details = new Cell().setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.RIGHT);
        details.add(new Paragraph("INVOICE DETAILS").setFont(bold).setFontSize(9).setFontColor(GREY));
        details.add(line("Issue date:  " + e.issueDate().format(DATE), regular));
        details.add(line("Due date:  " + e.dueDate().format(DATE), regular));
        details.add(line("Status:  " + e.status(), regular));

        t.addCell(to);
        t.addCell(details);
        doc.add(t);
    }

    private void addLineItems(Document doc, InvoiceReadyForDeliveryEvent e, PdfFont regular, PdfFont bold) {
        Table t = new Table(UnitValue.createPercentArray(new float[]{45, 10, 15, 10, 20}))
                .useAllAvailableWidth().setMarginTop(24);

        for (String h : new String[]{"DESCRIPTION", "QTY", "UNIT PRICE", "DISC", "TOTAL"}) {
            t.addHeaderCell(new Cell().add(new Paragraph(h).setFont(bold).setFontSize(9)));
        }

        e.lineItems().stream()
                .sorted((a, b) -> Integer.compare(
                        a.position() == null ? 0 : a.position(),
                        b.position() == null ? 0 : b.position()))
                .forEach(li -> {
                    t.addCell(cell(li.description(), regular, TextAlignment.LEFT));
                    t.addCell(cell(money(li.quantity()), regular, TextAlignment.CENTER));
                    t.addCell(cell(money(li.unitPrice()), regular, TextAlignment.RIGHT));
                    t.addCell(cell(money(li.discountPct()), regular, TextAlignment.CENTER));
                    t.addCell(cell(money(li.lineTotal()), regular, TextAlignment.RIGHT));
                });

        doc.add(t);
    }

    private void addTotals(Document doc, InvoiceReadyForDeliveryEvent e, PdfFont regular, PdfFont bold) {
        Table t = new Table(UnitValue.createPercentArray(new float[]{70, 30}))
                .useAllAvailableWidth().setMarginTop(16);
        totalRow(t, "Subtotal", money(e.subtotal()), regular, regular);
        totalRow(t, "Tax", money(e.taxAmount()), regular, regular);
        totalRow(t, "TOTAL", money(e.total()), bold, bold);
        doc.add(t);
    }

    private void totalRow(Table t, String label, String value, PdfFont l, PdfFont v) {
        t.addCell(new Cell().setBorder(Border.NO_BORDER)
                .add(new Paragraph(label).setFont(l).setFontSize(10).setTextAlignment(TextAlignment.RIGHT)));
        t.addCell(new Cell().setBorder(Border.NO_BORDER)
                .add(new Paragraph(value).setFont(v).setFontSize(10).setTextAlignment(TextAlignment.RIGHT)));
    }

    private Cell cell(String text, PdfFont font, TextAlignment align) {
        return new Cell().add(new Paragraph(text == null ? "" : text)
                .setFont(font).setFontSize(10).setTextAlignment(align));
    }

    private Paragraph line(String text, PdfFont font) {
        return new Paragraph(text).setFont(font).setFontSize(10).setFontColor(GREY);
    }

    private String money(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }
}