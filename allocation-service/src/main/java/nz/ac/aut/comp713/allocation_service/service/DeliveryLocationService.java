package nz.ac.aut.comp713.allocation_service.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import nz.ac.aut.comp713.allocation_service.exception.WeeklyAllocationNotFoundException;
import nz.ac.aut.comp713.allocation_service.model.DeliveryLocation;
import nz.ac.aut.comp713.allocation_service.model.DeliveryStatus;
import nz.ac.aut.comp713.allocation_service.repository.DeliveryLocationRepository;
import nz.ac.aut.comp713.allocation_service.repository.WeeklyAllocationRepository;
import nz.ac.aut.comp713.allocation_service.security.AllocationAccess;

@Service
public class DeliveryLocationService {
    public record Point(DeliveryLocation.Kind kind, double latitude, double longitude, Instant updatedAt) {
        public static Point from(DeliveryLocation location) {
            return new Point(location.getKind(), location.getLatitude(), location.getLongitude(),
                    location.getUpdatedAt());
        }
    }

    private final WeeklyAllocationRepository allocations;
    private final DeliveryLocationRepository locations;
    private final Clock clock;

    @Autowired
    public DeliveryLocationService(WeeklyAllocationRepository allocations, DeliveryLocationRepository locations) {
        this(allocations, locations, Clock.systemUTC());
    }

    DeliveryLocationService(WeeklyAllocationRepository allocations, DeliveryLocationRepository locations, Clock clock) {
        this.allocations = allocations;
        this.locations = locations;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Point> list(Long allocationId) {
        var allocation = allocations.findById(allocationId)
                .orElseThrow(() -> new WeeklyAllocationNotFoundException(allocationId));
        AllocationAccess.locationReader(allocation);
        return locations.findByAllocationId(allocationId).stream()
                .max((a, b) -> Integer.compare(priority(a.getKind()), priority(b.getKind())))
                .map(location -> List.of(new Point(DeliveryLocation.Kind.DRIVER,
                        location.getLatitude(), location.getLongitude(), location.getUpdatedAt())))
                .orElseGet(List::of);
    }

    private int priority(DeliveryLocation.Kind kind) {
        return switch (kind) {
            case START -> 0;
            case INTERMEDIATE -> 1;
            case LATEST -> 2;
            case DRIVER -> 3;
        };
    }

    @Transactional
    public Point save(Long allocationId, double latitude, double longitude) {
        var allocation = allocations.findById(allocationId)
                .orElseThrow(() -> new WeeklyAllocationNotFoundException(allocationId));
        AllocationAccess.locationWriter(allocation);
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid coordinates");
        }
        if (allocation.getDeliveryStatus() != DeliveryStatus.IN_TRANSIT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Delivery must be in transit");
        }
        var location = locations.findByAllocationIdAndKind(allocationId, DeliveryLocation.Kind.DRIVER)
                .orElseGet(() -> new DeliveryLocation(allocationId, DeliveryLocation.Kind.DRIVER));
        location.update(latitude, longitude, clock.instant());
        return Point.from(locations.saveAndFlush(location));
    }
}
