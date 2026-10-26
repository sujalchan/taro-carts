package nz.ac.aut.comp713.allocation_service.controller;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import nz.ac.aut.comp713.allocation_service.client.TaroClient;
import nz.ac.aut.comp713.allocation_service.client.TaroTypeResponse;
import nz.ac.aut.comp713.allocation_service.dto.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Provides taro type data needed by allocation workflows. */
@RestController
@RequestMapping("/api/v1/taro-service-reference")
@Tag(name = "Taro References", description = "Taro type data exposed for allocation workflows")
public class TaroServiceReferenceController {
    private final TaroClient taroClient;

    /** Creates the controller with the taro service client. */
    public TaroServiceReferenceController(TaroClient taroClient) {
        this.taroClient = taroClient;
    }

    /** Lists taro types available for allocation items. */
    @Operation(summary = "List taro types for allocations")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Taro type list retrieved", content = @Content(mediaType = "application/json", schema = @Schema(type = "array", implementation = TaroTypeResponse.class))),
            @ApiResponse(responseCode = "503", description = "Taro service is unavailable", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/taro-types")
    public List<TaroTypeResponse> getTaroTypes() {
        return taroClient.getTaroTypes();
    }
}
