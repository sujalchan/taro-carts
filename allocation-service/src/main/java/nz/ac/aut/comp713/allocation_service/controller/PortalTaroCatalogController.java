package nz.ac.aut.comp713.allocation_service.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import nz.ac.aut.comp713.allocation_service.client.TaroClient;
import nz.ac.aut.comp713.allocation_service.client.TaroTypeResponse;
import nz.ac.aut.comp713.allocation_service.security.AllocationAccess;

/** Small catalog for the customer request form; requires an authenticated allocation identity. */
@RestController
@RequestMapping("/api/v1/allocations/taro-types")
public class PortalTaroCatalogController {
    private final TaroClient taro;

    public PortalTaroCatalogController(TaroClient taro) {
        this.taro = taro;
    }

    @GetMapping
    public List<TaroTypeResponse> list() {
        if (AllocationAccess.customerForList(null) == null) {
            AllocationAccess.admin();
        }
        return taro.getTaroTypes();
    }
}
