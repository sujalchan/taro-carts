package nz.ac.aut.comp713.allocation_service.controller;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import nz.ac.aut.comp713.allocation_service.service.DeliveryLocationService;

@RestController
@RequestMapping("/api/v1/allocations/{allocationId}/locations")
public class DeliveryLocationController {
    public record LocationRequest(Double latitude, Double longitude) {}
    private final DeliveryLocationService service;
    public DeliveryLocationController(DeliveryLocationService service) { this.service = service; }

    @GetMapping
    public List<DeliveryLocationService.Point> list(@PathVariable Long allocationId) {
        return service.list(allocationId);
    }

    @PostMapping
    public ResponseEntity<DeliveryLocationService.Point> save(@PathVariable Long allocationId,
            @RequestBody LocationRequest request) {
        if (request.latitude() == null || request.longitude() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Coordinates are required");
        }
        return ResponseEntity.ok(service.save(allocationId, request.latitude(), request.longitude()));
    }
}
