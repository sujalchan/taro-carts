package nz.ac.aut.comp713.allocation_service.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import nz.ac.aut.comp713.allocation_service.client.CustomerClient;
import nz.ac.aut.comp713.allocation_service.client.CustomerResponse;
import nz.ac.aut.comp713.allocation_service.client.TaroClient;
import nz.ac.aut.comp713.allocation_service.client.TaroTypeResponse;
import nz.ac.aut.comp713.allocation_service.exception.InvoiceGenerationException;
import nz.ac.aut.comp713.allocation_service.exception.InvoiceNotAvailableException;
import nz.ac.aut.comp713.allocation_service.exception.InvalidInvoiceAmendmentException;
import nz.ac.aut.comp713.allocation_service.exception.WeeklyAllocationNotFoundException;
import nz.ac.aut.comp713.allocation_service.model.AllocationItem;
import nz.ac.aut.comp713.allocation_service.model.DeliveryStatus;
import nz.ac.aut.comp713.allocation_service.model.Invoice;
import nz.ac.aut.comp713.allocation_service.model.InvoiceRevision;
import nz.ac.aut.comp713.allocation_service.model.WeeklyAllocation;
import nz.ac.aut.comp713.allocation_service.repository.AllocationItemRepository;
import nz.ac.aut.comp713.allocation_service.repository.InvoiceRepository;
import nz.ac.aut.comp713.allocation_service.repository.InvoiceRevisionRepository;
import nz.ac.aut.comp713.allocation_service.repository.WeeklyAllocationRepository;
import nz.ac.aut.comp713.allocation_service.security.AllocationAccess;

@Service
public class InvoiceService {
    private final InvoiceRepository invoiceRepository;
    private final InvoiceRevisionRepository revisionRepository;
    private final WeeklyAllocationRepository allocationRepository;
    private final AllocationItemRepository itemRepository;
    private final CustomerClient customerClient;
    private final TaroClient taroClient;
    private final InvoicePdfGenerator pdfGenerator;

    public InvoiceService(InvoiceRepository invoiceRepository, InvoiceRevisionRepository revisionRepository,
            WeeklyAllocationRepository allocationRepository, AllocationItemRepository itemRepository,
            CustomerClient customerClient, TaroClient taroClient, InvoicePdfGenerator pdfGenerator) {
        this.invoiceRepository = invoiceRepository;
        this.revisionRepository = revisionRepository;
        this.allocationRepository = allocationRepository;
        this.itemRepository = itemRepository;
        this.customerClient = customerClient;
        this.taroClient = taroClient;
        this.pdfGenerator = pdfGenerator;
    }

    boolean hasInvoice(Long allocationId) {
        return invoiceRepository.existsByAllocationId(allocationId);
    }

    // Also handles allocations marked delivered before this feature was introduced.
    @Transactional
    public Download download(Long allocationId) {
        WeeklyAllocation authorizedAllocation = allocationRepository.findById(allocationId)
                .orElseThrow(() -> new WeeklyAllocationNotFoundException(allocationId));
        AllocationAccess.view(authorizedAllocation);
        Invoice invoice = invoiceRepository.findByAllocationId(allocationId)
                .orElseGet(() -> {
                    if (authorizedAllocation.getDeliveryStatus() != DeliveryStatus.DELIVERED) {
                        throw new InvoiceNotAvailableException(allocationId);
                    }
                    return issueIfDelivered(authorizedAllocation);
                });
        if (!Integer.valueOf(InvoicePdfGenerator.TEMPLATE_VERSION).equals(invoice.getPdfTemplateVersion())) {
            refreshPdf(invoice);
        }
        return new Download("invoice-" + invoice.getInvoiceNumber() + ".pdf", invoice.getPdfBytes());
    }

    private void refreshPdf(Invoice invoice) {
        WeeklyAllocation allocation = allocationRepository.findById(invoice.getAllocationId())
                .orElseThrow(() -> new WeeklyAllocationNotFoundException(invoice.getAllocationId()));
        refreshPdf(invoice, allocation);
    }

    private void refreshPdf(Invoice invoice, WeeklyAllocation allocation) {
        InvoicePdfGenerator.Document document = buildDocument(allocation,
                invoice.getInvoiceNumber(), invoice.getIssuedOn());
        if (document.total().compareTo(invoice.getTotalAmount()) != 0
                || !allocation.getCustomerId().equals(invoice.getCustomerId())) {
            throw new InvoiceGenerationException(new IllegalStateException(
                    "Saved invoice total or customer no longer matches the allocation"));
        }
        byte[] refreshedPdf = pdfGenerator.render(document);
        if (invoice.getOriginalPdfBytes() == null) {
            invoice.setOriginalPdfBytes(invoice.getPdfBytes());
        }
        invoice.setPdfBytes(refreshedPdf);
        invoice.setPdfTemplateVersion(InvoicePdfGenerator.TEMPLATE_VERSION);
        invoiceRepository.saveAndFlush(invoice);
    }

    void refreshPaymentStatus(WeeklyAllocation allocation) {
        invoiceRepository.findByAllocationId(allocation.getId())
                .ifPresent(invoice -> refreshPdf(invoice, allocation));
    }

    @Transactional
    Invoice issueIfDelivered(WeeklyAllocation allocation) {
        if (allocation.getDeliveryStatus() != DeliveryStatus.DELIVERED) {
            throw new InvoiceNotAvailableException(allocation.getId());
        }
        Invoice existing = invoiceRepository.findByAllocationId(allocation.getId()).orElse(null);
        if (existing != null)
            return existing;

        String number = "INV-" + allocation.getId();
        LocalDate issuedOn = LocalDate.now(ZoneId.of("Pacific/Auckland"));
        InvoicePdfGenerator.Document document = buildDocument(allocation, number, issuedOn);

        Invoice invoice = new Invoice();
        invoice.setAllocationId(allocation.getId());
        invoice.setCustomerId(allocation.getCustomerId());
        invoice.setInvoiceNumber(number);
        invoice.setIssuedOn(issuedOn);
        invoice.setTotalAmount(document.total());
        invoice.setPdfBytes(pdfGenerator.render(document));
        invoice.setPdfTemplateVersion(InvoicePdfGenerator.TEMPLATE_VERSION);
        return invoiceRepository.saveAndFlush(invoice);
    }

    private InvoicePdfGenerator.Document buildDocument(WeeklyAllocation allocation,
            String number, LocalDate issuedOn) {
        // keep reference lookups local to this document build so repeated ids are fetched once
        Map<Long, CustomerResponse> customers = new HashMap<>();
        Map<Long, TaroTypeResponse> taroTypes = new HashMap<>();

        CustomerResponse customer = customers.computeIfAbsent(
                allocation.getCustomerId(),
                customerClient::getCustomer);
        List<InvoicePdfGenerator.Line> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO.setScale(2);

        for (AllocationItem item : itemRepository.findByWeeklyAllocationId(allocation.getId())) {
            TaroTypeResponse taroType = taroTypes.computeIfAbsent(
                    item.getTaroTypeId(),
                    taroClient::getTaroType);
            BigDecimal price = item.getPricePerKg().setScale(2, RoundingMode.HALF_UP);
            BigDecimal amount = price.multiply(BigDecimal.valueOf(item.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);
            lines.add(new InvoicePdfGenerator.Line(taroType.name(), item.getQuantity(), price, amount));
            total = total.add(amount);
        }

        // Saved prices include GST. The tax fraction of a GST-inclusive total is 3/23.
        BigDecimal gst = total.multiply(BigDecimal.valueOf(3))
                .divide(BigDecimal.valueOf(23), 2, RoundingMode.HALF_UP);
        BigDecimal subtotal = total.subtract(gst);
        return new InvoicePdfGenerator.Document(
                number,
                issuedOn,
                customer.name(),
                customer.phone(),
                allocation.getWeekStart(),
                allocation.getPaymentStatus(),
                lines,
                subtotal,
                gst,
                total);
    }

    public record Download(String filename, byte[] bytes) {
    }

    public record PriceChange(Long itemId, BigDecimal pricePerKg) {}
    public record RevisionInfo(int revisionNumber, Instant changedAt, String reason, BigDecimal previousTotal) {}

    @Transactional
    public void amendPrices(Long allocationId, List<PriceChange> changes, String reason) {
        Long actorAccountId = AllocationAccess.adminAccountId();
        if (reason == null || reason.trim().length() < 8 || reason.trim().length() > 500) {
            throw new InvalidInvoiceAmendmentException("Enter an amendment reason of 8–500 characters.");
        }
        Invoice invoice = invoiceRepository.findByAllocationId(allocationId)
                .orElseThrow(() -> new InvoiceNotAvailableException(allocationId));
        WeeklyAllocation allocation = allocationRepository.findById(allocationId)
                .orElseThrow(() -> new WeeklyAllocationNotFoundException(allocationId));
        List<AllocationItem> items = itemRepository.findByWeeklyAllocationId(allocationId);
        if (changes == null || changes.size() != items.size() || items.isEmpty()) {
            throw new InvalidInvoiceAmendmentException("Supply one price for every invoiced item.");
        }
        Map<Long, AllocationItem> byId = new HashMap<>();
        items.forEach(item -> byId.put(item.getId(), item));
        Set<Long> seen = new HashSet<>();
        boolean changed = false;
        for (PriceChange change : changes) {
            if (change == null || change.itemId() == null || !seen.add(change.itemId())
                    || !byId.containsKey(change.itemId()) || change.pricePerKg() == null
                    || change.pricePerKg().signum() < 0 || change.pricePerKg().scale() > 2
                    || change.pricePerKg().precision() > 10) {
                throw new InvalidInvoiceAmendmentException("Each item needs a valid price with at most two decimal places.");
            }
            if (byId.get(change.itemId()).getPricePerKg().compareTo(change.pricePerKg()) != 0) changed = true;
        }
        if (!changed) throw new InvalidInvoiceAmendmentException("Change at least one item price.");

        int revisionNumber = Math.toIntExact(revisionRepository.countByInvoiceId(invoice.getId()) + 1);
        InvoiceRevision previous = new InvoiceRevision(invoice.getId(), revisionNumber, actorAccountId,
                reason.trim(), invoice.getTotalAmount(), invoice.getPdfBytes());
        for (PriceChange change : changes) byId.get(change.itemId()).setPricePerKg(change.pricePerKg());
        itemRepository.saveAllAndFlush(items);
        InvoicePdfGenerator.Document document = buildDocument(allocation,
                invoice.getInvoiceNumber(), invoice.getIssuedOn());
        byte[] amendedPdf = pdfGenerator.render(document);
        if (invoice.getOriginalPdfBytes() == null) invoice.setOriginalPdfBytes(invoice.getPdfBytes());
        invoice.setTotalAmount(document.total());
        invoice.setPdfBytes(amendedPdf);
        invoice.setPdfTemplateVersion(InvoicePdfGenerator.TEMPLATE_VERSION);
        revisionRepository.saveAndFlush(previous);
        invoiceRepository.saveAndFlush(invoice);
    }

    public List<RevisionInfo> revisions(Long allocationId) {
        AllocationAccess.admin();
        Invoice invoice = invoiceRepository.findByAllocationId(allocationId)
                .orElseThrow(() -> new InvoiceNotAvailableException(allocationId));
        return revisionRepository.findByInvoiceIdOrderByRevisionNumberDesc(invoice.getId()).stream()
                .map(revision -> new RevisionInfo(revision.getRevisionNumber(), revision.getChangedAt(),
                        revision.getReason(), revision.getPreviousTotal()))
                .toList();
    }

    public Download downloadRevision(Long allocationId, int revisionNumber) {
        AllocationAccess.admin();
        Invoice invoice = invoiceRepository.findByAllocationId(allocationId)
                .orElseThrow(() -> new InvoiceNotAvailableException(allocationId));
        InvoiceRevision revision = revisionRepository
                .findByInvoiceIdAndRevisionNumber(invoice.getId(), revisionNumber)
                .orElseThrow(() -> new InvoiceNotAvailableException(allocationId));
        return new Download("invoice-" + invoice.getInvoiceNumber() + "-revision-"
                + revisionNumber + ".pdf", revision.getPreviousPdfBytes());
    }
}
