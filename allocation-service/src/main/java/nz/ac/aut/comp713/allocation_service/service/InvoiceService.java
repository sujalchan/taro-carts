package nz.ac.aut.comp713.allocation_service.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import nz.ac.aut.comp713.allocation_service.client.CustomerClient;
import nz.ac.aut.comp713.allocation_service.client.TaroClient;
import nz.ac.aut.comp713.allocation_service.exception.InvoiceNotAvailableException;
import nz.ac.aut.comp713.allocation_service.exception.WeeklyAllocationNotFoundException;
import nz.ac.aut.comp713.allocation_service.model.AllocationItem;
import nz.ac.aut.comp713.allocation_service.model.DeliveryStatus;
import nz.ac.aut.comp713.allocation_service.model.Invoice;
import nz.ac.aut.comp713.allocation_service.model.WeeklyAllocation;
import nz.ac.aut.comp713.allocation_service.repository.AllocationItemRepository;
import nz.ac.aut.comp713.allocation_service.repository.InvoiceRepository;
import nz.ac.aut.comp713.allocation_service.repository.WeeklyAllocationRepository;

@Service
public class InvoiceService {
    private final InvoiceRepository invoiceRepository;
    private final WeeklyAllocationRepository allocationRepository;
    private final AllocationItemRepository itemRepository;
    private final CustomerClient customerClient;
    private final TaroClient taroClient;
    private final InvoicePdfGenerator pdfGenerator;

    public InvoiceService(InvoiceRepository invoiceRepository,
            WeeklyAllocationRepository allocationRepository, AllocationItemRepository itemRepository,
            CustomerClient customerClient, TaroClient taroClient, InvoicePdfGenerator pdfGenerator) {
        this.invoiceRepository = invoiceRepository;
        this.allocationRepository = allocationRepository;
        this.itemRepository = itemRepository;
        this.customerClient = customerClient;
        this.taroClient = taroClient;
        this.pdfGenerator = pdfGenerator;
    }

    public boolean hasInvoice(Long allocationId) {
        return invoiceRepository.existsByAllocationId(allocationId);
    }

    // Also handles allocations marked delivered before this feature was introduced.
    @Transactional
    public Download download(Long allocationId) {
        Invoice invoice = invoiceRepository.findByAllocationId(allocationId)
                .orElseGet(() -> {
                    WeeklyAllocation allocation = allocationRepository.findById(allocationId)
                            .orElseThrow(() -> new WeeklyAllocationNotFoundException(allocationId));
                    if (allocation.getDeliveryStatus() != DeliveryStatus.DELIVERED) {
                        throw new InvoiceNotAvailableException(allocationId);
                    }
                    return issueIfDelivered(allocation);
                });
        return new Download("invoice-" + invoice.getInvoiceNumber() + ".pdf", invoice.getPdfBytes());
    }

    @Transactional
    public Invoice issueIfDelivered(WeeklyAllocation allocation) {
        if (allocation.getDeliveryStatus() != DeliveryStatus.DELIVERED) {
            throw new InvoiceNotAvailableException(allocation.getId());
        }
        Invoice existing = invoiceRepository.findByAllocationId(allocation.getId()).orElse(null);
        if (existing != null) return existing;

        var customer = customerClient.getCustomer(allocation.getCustomerId());
        List<InvoicePdfGenerator.Line> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO.setScale(2);

        for (AllocationItem item : itemRepository.findByWeeklyAllocationId(allocation.getId())) {
            String productName = taroClient.getTaroType(item.getTaroTypeId()).name();
            BigDecimal price = item.getPricePerKg().setScale(2, RoundingMode.HALF_UP);
            BigDecimal amount = price.multiply(BigDecimal.valueOf(item.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);
            lines.add(new InvoicePdfGenerator.Line(productName, item.getQuantity(), price, amount));
            total = total.add(amount);
        }

        String number = "INV-" + allocation.getId();
        LocalDate issuedOn = LocalDate.now(ZoneId.of("Pacific/Auckland"));
        // Saved prices include GST. The tax fraction of a GST-inclusive total is 3/23.
        BigDecimal gst = total.multiply(BigDecimal.valueOf(3))
                .divide(BigDecimal.valueOf(23), 2, RoundingMode.HALF_UP);
        BigDecimal subtotal = total.subtract(gst);
        byte[] pdf = pdfGenerator.render(new InvoicePdfGenerator.Document(number, issuedOn,
                customer.name(), customer.phone(), allocation.getWeekStart(), lines, subtotal, gst, total));

        Invoice invoice = new Invoice();
        invoice.setAllocationId(allocation.getId());
        invoice.setCustomerId(allocation.getCustomerId());
        invoice.setInvoiceNumber(number);
        invoice.setIssuedOn(issuedOn);
        invoice.setTotalAmount(total);
        invoice.setPdfBytes(pdf);
        return invoiceRepository.saveAndFlush(invoice);
    }

    public record Download(String filename, byte[] bytes) {}
}
