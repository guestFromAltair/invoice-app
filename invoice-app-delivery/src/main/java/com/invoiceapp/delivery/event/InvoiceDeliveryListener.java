package com.invoiceapp.delivery.event;

import com.invoiceapp.delivery.domain.DeliveryAttempt;
import com.invoiceapp.delivery.domain.DeliveryAttemptRepository;
import com.invoiceapp.delivery.domain.DeliveryStatus;
import com.invoiceapp.delivery.pdf.InvoicePdfRenderer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
@RequiredArgsConstructor
@Slf4j
public class InvoiceDeliveryListener {
    private final DeliveryAttemptRepository repository;
    private final InvoicePdfRenderer renderer;
    private final JsonMapper jsonMapper;

    @Value("${application.delivery.pdf-output-dir}")
    private String pdfOutputDir;

    @KafkaListener(topics = "${application.kafka.topic.invoice-delivery}")
    @Transactional
    public void onInvoiceReadyForDelivery(ConsumerRecord<String, String> record) throws Exception {
        InvoiceReadyForDeliveryEvent event =
                jsonMapper.readValue(record.value(), InvoiceReadyForDeliveryEvent.class);

        if (repository.existsByInvoiceId(event.invoiceId())) {
            log.info("Invoice {} already has a delivery record, skipping", event.invoiceNumber());
            return;
        }

        DeliveryAttempt attempt = DeliveryAttempt.builder()
                .invoiceId(event.invoiceId())
                .invoiceNumber(event.invoiceNumber())
                .ownerId(event.ownerId())
                .recipient(event.recipient().email())
                .status(DeliveryStatus.PENDING)
                .attempts(0)
                .build();

        try {
            repository.save(attempt);
        } catch (DataIntegrityViolationException race) {
            log.info("Invoice {} claimed by a concurrent delivery, skipping", event.invoiceNumber());
            return;
        }

        byte[] pdf = renderer.render(event);
        attempt.setStatus(DeliveryStatus.RENDERED);

        writeForInspection(event, pdf);
        log.info("Rendered invoice {} for {} ({} bytes)", event.invoiceNumber(), event.recipient().email(), pdf.length);
    }

    private void writeForInspection(InvoiceReadyForDeliveryEvent event, byte[] pdf) {
        try {
            Path dir = Path.of(pdfOutputDir);
            Files.createDirectories(dir);
            Files.write(dir.resolve(event.invoiceNumber() + ".pdf"), pdf);
        } catch (Exception e) {
            log.warn("Could not write PDF for inspection", e);
        }
    }
}