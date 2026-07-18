package com.invoiceapp.backend.invoice.scheduler;

import com.invoiceapp.backend.invoice.service.InvoiceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OverdueDetectionJob")
class OverdueDetectionJobTest {

    @Mock
    private InvoiceService invoiceService;

    @InjectMocks
    private OverdueDetectionJob overdueDetectionJob;

    @Test
    @DisplayName("delegates to the service to mark overdue invoices")
    void delegates_to_service() {
        when(invoiceService.markOverdueInvoices()).thenReturn(3);

        overdueDetectionJob.detectOverdueInvoices();

        verify(invoiceService).markOverdueInvoices();
    }

    @Test
    @DisplayName("still runs cleanly when nothing is overdue")
    void handles_zero() {
        when(invoiceService.markOverdueInvoices()).thenReturn(0);

        overdueDetectionJob.detectOverdueInvoices();

        verify(invoiceService).markOverdueInvoices();
    }
}