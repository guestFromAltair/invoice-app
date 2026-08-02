package com.invoiceapp.delivery.email;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InvoiceEmailSender {
    private final JavaMailSender mailSender;

    @Value("${application.delivery.from-address}")
    private String fromAddress;

    public void send(String to, String invoiceNumber, String recipientName, byte[] pdf) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(fromAddress);
        helper.setTo(to);
        helper.setSubject("Invoice " + invoiceNumber);
        helper.setText("""
                Hello %s,

                Please find invoice %s attached.

                Thank you,
                InvoiceApp
                """.formatted(recipientName, invoiceNumber), false);
        helper.addAttachment(invoiceNumber + ".pdf", new ByteArrayResource(pdf), "application/pdf");

        mailSender.send(message);
    }

    public static boolean isValidAddress(String email) {
        try {
            new InternetAddress(email, true).validate();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}