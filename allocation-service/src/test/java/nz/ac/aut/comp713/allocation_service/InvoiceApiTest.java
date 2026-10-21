package nz.ac.aut.comp713.allocation_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.stream.IntStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import nz.ac.aut.comp713.allocation_service.client.CustomerClient;
import nz.ac.aut.comp713.allocation_service.client.CustomerResponse;
import nz.ac.aut.comp713.allocation_service.client.TaroClient;
import nz.ac.aut.comp713.allocation_service.client.TaroTypeResponse;
import nz.ac.aut.comp713.allocation_service.model.DeliveryStatus;
import nz.ac.aut.comp713.allocation_service.repository.AllocationItemRepository;
import nz.ac.aut.comp713.allocation_service.repository.InvoiceRepository;
import nz.ac.aut.comp713.allocation_service.repository.WeeklyAllocationRepository;
import nz.ac.aut.comp713.allocation_service.service.InvoicePdfGenerator;

@SpringBootTest
@AutoConfigureMockMvc
class InvoiceApiTest {
    @Autowired MockMvc mockMvc;
    @Autowired InvoiceRepository invoiceRepository;
    @Autowired AllocationItemRepository itemRepository;
    @Autowired WeeklyAllocationRepository allocationRepository;
    @Autowired InvoicePdfGenerator pdfGenerator;
    @MockitoBean CustomerClient customerClient;
    @MockitoBean TaroClient taroClient;

    @BeforeEach
    void setUp() {
        invoiceRepository.deleteAll();
        itemRepository.deleteAll();
        allocationRepository.deleteAll();
        when(customerClient.getCustomer(1L))
                .thenReturn(new CustomerResponse(1L, "Island Foods", "John", "0211234567", true));
        when(taroClient.getTaroType(1L))
                .thenReturn(new TaroTypeResponse(1L, "Samoan Taro", "", new BigDecimal("50.00")));
    }

    @Test
    void invoiceIsUnavailableBeforeDelivery() throws Exception {
        Long id = createAllocation();
        mockMvc.perform(get("/api/v1/allocations/{id}/invoice.pdf", id)
                .accept(MediaType.APPLICATION_PDF))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVOICE_NOT_AVAILABLE"));
        assertThat(invoiceRepository.count()).isZero();
    }

    @Test
    void missingAllocationReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/allocations/999999/invoice.pdf")
                .accept(MediaType.APPLICATION_PDF))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WEEKLY_ALLOCATION_NOT_FOUND"));
    }

    @Test
    void deliveryIssuesOneDownloadablePdfWithSavedTotal() throws Exception {
        Long id = createAllocation();
        mockMvc.perform(put("/api/v1/allocations/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(update("DELIVERED", 100)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceAvailable").value(true));

        assertThat(invoiceRepository.count()).isEqualTo(1);
        assertThat(invoiceRepository.findByAllocationId(id).orElseThrow().getTotalAmount())
                .isEqualByComparingTo("5000.00");

        byte[] first = mockMvc.perform(get("/api/v1/allocations/{id}/invoice.pdf", id))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andReturn().getResponse().getContentAsByteArray();

        byte[] viewed = mockMvc.perform(get("/api/v1/allocations/{id}/invoice.pdf", id)
                .param("inline", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("inline")))
                .andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(first, viewed);
        mockMvc.perform(get("/allocations"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("View Invoice PDF")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("inline=true")));

        assertThat(new String(first, 0, 5, java.nio.charset.StandardCharsets.US_ASCII))
                .isEqualTo("%PDF-");
        try (var pdf = Loader.loadPDF(first)) {
            String text = new PDFTextStripper().getText(pdf);
            assertThat(text).contains("INV-" + id, "Island Foods", "Samoan Taro",
                    "DESCRIPTION", "QTY (KG)", "RATE", "AMOUNT", "GST (15%)",
                    "Subtotal (excl GST)", "$4,347.83", "$652.17", "TOTAL (NZD)", "$5,000.00",
                    "AUT City Campus", "55 Wellesley Street East", "Auckland Central 1010",
                    "GST number: 000000000 (EXAMPLE ONLY)",
                    "EXAMPLE FORMAT - NOT TO BE USED AS BILLABLE INVOICE");
        }

        // The issued document remains unchanged after the live allocation is edited.
        when(taroClient.getTaroType(1L))
                .thenReturn(new TaroTypeResponse(1L, "Renamed Taro", "", new BigDecimal("75.00")));
        mockMvc.perform(put("/api/v1/allocations/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(update("CANCELLED", 100)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceAvailable").value(true));

        mockMvc.perform(put("/api/v1/allocations/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"customerId":1,"weekStart":"2026-09-14",
                         "paymentStatus":"PAID",
                         "allocationItems":[{"taroTypeId":1,"quantity":100}]}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.allocationItems[0].pricePerKg").value(50.00));

        mockMvc.perform(put("/api/v1/allocations/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(update("CANCELLED", 200)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ISSUED_INVOICE_CONFLICT"));
        mockMvc.perform(delete("/api/v1/allocations/{id}", id))
                .andExpect(status().isConflict());

        byte[] second = mockMvc.perform(get("/api/v1/allocations/{id}/invoice.pdf", id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(first, second);
        assertThat(invoiceRepository.count()).isEqualTo(1);
    }

    @Test
    void firstDownloadIssuesAnOlderDeliveredAllocation() throws Exception {
        Long id = createAllocation();
        var allocation = allocationRepository.findById(id).orElseThrow();
        allocation.setDeliveryStatus(DeliveryStatus.DELIVERED);
        allocationRepository.saveAndFlush(allocation);

        assertThat(invoiceRepository.count()).isZero();
        mockMvc.perform(get("/api/v1/allocations/{id}/invoice.pdf", id))
                .andExpect(status().isOk());
        assertThat(invoiceRepository.count()).isEqualTo(1);
    }

    @Test
    void longInvoiceContinuesTableOnAnotherPage() throws Exception {
        var lines = IntStream.range(0, 30)
                .mapToObj(index -> new InvoicePdfGenerator.Line("Samoan Taro batch " + index,
                        1, new BigDecimal("10.00"), new BigDecimal("10.00")))
                .toList();
        var document = new InvoicePdfGenerator.Document("INV-MULTI", LocalDate.of(2026, 9, 21),
                "Island Foods", "0211234567", LocalDate.of(2026, 9, 14), lines,
                new BigDecimal("260.87"), new BigDecimal("39.13"), new BigDecimal("300.00"));

        try (var pdf = Loader.loadPDF(pdfGenerator.render(document))) {
            String text = new PDFTextStripper().getText(pdf);
            assertThat(pdf.getNumberOfPages()).isGreaterThan(1);
            assertThat(text).contains("Page 2", "Samoan Taro batch 29", "TOTAL (NZD)", "$300.00");
            assertThat(text.split("DESCRIPTION", -1).length).isGreaterThan(2);
        }
    }

    private Long createAllocation() throws Exception {
        mockMvc.perform(post("/api/v1/allocations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"customerId":1,"weekStart":"2026-09-14",
                         "allocationItems":[{"taroTypeId":1,"quantity":100}]}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invoiceAvailable").value(false));
        return allocationRepository.findAll().getFirst().getId();
    }

    private String update(String status, int quantity) {
        return """
                {"customerId":1,"weekStart":"2026-09-14",
                 "deliveryStatus":"%s",
                 "allocationItems":[{"taroTypeId":1,"quantity":%d,"pricePerKg":50.00}]}
                """.formatted(status, quantity);
    }
}
