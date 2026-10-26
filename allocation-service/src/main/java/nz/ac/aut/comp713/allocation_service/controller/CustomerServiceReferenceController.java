package nz.ac.aut.comp713.allocation_service.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import nz.ac.aut.comp713.allocation_service.client.CustomerClient;
import nz.ac.aut.comp713.allocation_service.client.TaroClient;
import nz.ac.aut.comp713.allocation_service.client.CustomerResponse;

/** Provides customer-service data needed by allocation clients. */
@RestController
@RequestMapping("/api/v1/customer-service-reference")
@Tag(name = "Customer References", description = "Customer data exposed for allocation workflows")
public class CustomerServiceReferenceController {

    private final CustomerClient customerClient;
    private final TaroClient taroClient;

    /** Creates the controller with clients for customer and taro reference data. */
    public CustomerServiceReferenceController(CustomerClient customerClient, TaroClient taroClient) {
        this.customerClient = customerClient;
        this.taroClient = taroClient;
    }

    /** Lists active customers by default, optionally including inactive records. */
    @Operation(summary = "List customers for allocations", description = "Returns active customers by default. Set includeInactive=true to include inactive customers.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Customer list retrieved", content = @Content(mediaType = "application/json", schema = @Schema(type = "array", implementation = CustomerResponse.class))),
            @ApiResponse(responseCode = "503", description = "Customer service is unavailable", content = @Content(mediaType = "application/json", schema = @Schema(implementation = nz.ac.aut.comp713.allocation_service.dto.ApiError.class)))
    })
    @GetMapping("/customers")
    public List<CustomerResponse> getCustomers(
            @Parameter(description = "Include inactive customers in the result", schema = @Schema(defaultValue = "false"))
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        List<CustomerResponse> customers = customerClient.getCustomers();
        if (includeInactive) {
            return customers;
        }
        return customers.stream()
                .filter(customer -> Boolean.TRUE.equals(customer.active()))
                .toList();
    }

}
