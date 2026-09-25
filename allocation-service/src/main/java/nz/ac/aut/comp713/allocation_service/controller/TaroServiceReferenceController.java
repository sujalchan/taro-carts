package nz.ac.aut.comp713.allocation_service.controller;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import nz.ac.aut.comp713.allocation_service.client.TaroClient;
import nz.ac.aut.comp713.allocation_service.client.TaroTypeResponse;

@RestController
@RequestMapping("/api/v1/taro-service-reference")
public class TaroServiceReferenceController {
    private final TaroClient taroClient;

    public TaroServiceReferenceController(TaroClient taroClient) {
        this.taroClient = taroClient;
    }

    @GetMapping("/taro-types")
    public List<TaroTypeResponse> getTaroTypes() {
        return taroClient.getTaroTypes();
    }
}
