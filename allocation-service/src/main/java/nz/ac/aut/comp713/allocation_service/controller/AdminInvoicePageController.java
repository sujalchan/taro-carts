package nz.ac.aut.comp713.allocation_service.controller;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import nz.ac.aut.comp713.allocation_service.exception.InvalidInvoiceAmendmentException;
import nz.ac.aut.comp713.allocation_service.service.InvoiceService;
import nz.ac.aut.comp713.allocation_service.service.WeeklyAllocationService;

/** Explicit admin-only financial amendment form; previous PDFs remain downloadable. */
@Controller
public class AdminInvoicePageController {
    private final InvoiceService invoices;
    private final WeeklyAllocationService allocations;

    public AdminInvoicePageController(InvoiceService invoices, WeeklyAllocationService allocations) {
        this.invoices = invoices;
        this.allocations = allocations;
    }

    @GetMapping("/allocations/{id}/invoice/edit")
    public String form(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean updated, Model model) {
        populate(id, model);
        model.addAttribute("updated", updated);
        return "invoice-edit-form";
    }

    @PostMapping("/allocations/{id}/invoice/edit")
    public String amend(@PathVariable Long id,
                        @RequestParam(name = "itemId", required = false) List<Long> itemIds,
                        @RequestParam(name = "pricePerKg", required = false) List<String> prices,
                        @RequestParam(required = false) String reason, Model model) {
        List<String> errors = new ArrayList<>();
        List<InvoiceService.PriceChange> changes = new ArrayList<>();
        if (itemIds == null || prices == null || itemIds.size() != prices.size()) {
            errors.add("Supply one price for every invoiced item.");
        } else {
            for (int index = 0; index < itemIds.size(); index++) {
                try {
                    changes.add(new InvoiceService.PriceChange(itemIds.get(index), new BigDecimal(prices.get(index))));
                } catch (NumberFormatException | NullPointerException exception) {
                    errors.add("Enter a valid price for every item.");
                    break;
                }
            }
        }
        if (errors.isEmpty()) {
            try {
                invoices.amendPrices(id, changes, reason);
                return "redirect:/allocations/" + id + "/invoice/edit?updated=true";
            } catch (InvalidInvoiceAmendmentException exception) {
                errors.add(exception.getMessage());
            }
        }
        populate(id, model);
        model.addAttribute("errors", errors);
        model.addAttribute("reason", reason);
        return "invoice-edit-form";
    }

    private void populate(Long id, Model model) {
        model.addAttribute("allocation", allocations.getWeeklyAllocationById(id));
        invoices.download(id); // Delivered legacy allocations issue their invoice on first access.
        model.addAttribute("revisions", invoices.revisions(id));
    }
}
