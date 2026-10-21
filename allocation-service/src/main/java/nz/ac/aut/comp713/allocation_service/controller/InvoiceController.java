package nz.ac.aut.comp713.allocation_service.controller;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import nz.ac.aut.comp713.allocation_service.service.InvoiceService;

@RestController
@RequestMapping("/api/v1/allocations")
public class InvoiceController {
    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    // Authentication and customer ownership checks belong here once the auth system lands.
    @GetMapping("/{id}/invoice.pdf")
    public ResponseEntity<byte[]> downloadInvoice(@PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean inline) {
        InvoiceService.Download download = invoiceService.download(id);
        ContentDisposition disposition = inline
                ? ContentDisposition.inline().filename(download.filename()).build()
                : ContentDisposition.attachment().filename(download.filename()).build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(download.bytes().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(download.bytes());
    }
}
