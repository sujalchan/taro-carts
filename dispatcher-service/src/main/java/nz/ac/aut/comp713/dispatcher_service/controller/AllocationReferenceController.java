package nz.ac.aut.comp713.dispatcher_service.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import nz.ac.aut.comp713.dispatcher_service.dto.DispatcherResponse;
import nz.ac.aut.comp713.dispatcher_service.service.DispatcherService;

/** Minimal assignment data needed by allocation-service, behind its private read key. */
@RestController
@RequestMapping("/internal/v1/allocation-reference/dispatchers")
public class AllocationReferenceController {
    public record DispatcherReference(Long id, String name, boolean active) {
        static DispatcherReference from(DispatcherResponse dispatcher) {
            return new DispatcherReference(dispatcher.id(), dispatcher.name(), dispatcher.active());
        }
    }

    private final DispatcherService dispatchers;

    public AllocationReferenceController(DispatcherService dispatchers) {
        this.dispatchers = dispatchers;
    }

    @GetMapping
    public List<DispatcherReference> list() {
        return dispatchers.getAll(null).stream().map(DispatcherReference::from).toList();
    }

    @GetMapping("/{id}")
    public DispatcherReference get(@PathVariable Long id) {
        return DispatcherReference.from(dispatchers.getById(id));
    }
}
