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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import nz.ac.aut.comp713.allocation_service.dto.ApiError;
import nz.ac.aut.comp713.allocation_service.service.InvoiceService;

/** REST endpoint that downloads an issued invoice for a delivered allocation. */
@RestController
@RequestMapping("/api/v1/allocations")
@Tag(name = "Invoices", description = "Invoice PDF downloads")
public class InvoiceController {
    private final InvoiceService invoiceService;

    /** Creates the controller with the invoice download service. */
    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    /** Downloads the allocation invoice as an attachment or inline PDF. */
    @Operation(summary = "Download an allocation invoice", description = "Returns a PDF invoice for a delivered allocation. The inline query parameter controls the Content-Disposition response header.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Invoice PDF returned", content = @Content(mediaType = "application/pdf", schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "404", description = "Allocation or referenced customer/taro type does not exist, or an invoice is not yet available", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "503", description = "Customer or taro service is unavailable while generating the invoice", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Invoice PDF could not be generated", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{id}/invoice.pdf")
    public ResponseEntity<byte[]> downloadInvoice(
            @Parameter(description = "Allocation identifier", required = true, schema = @Schema(type = "integer", format = "int64", minimum = "1")) @PathVariable Long id,
            @Parameter(description = "If true, display the PDF inline; otherwise download it as an attachment", schema = @Schema(defaultValue = "false"))
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
