package nz.ac.aut.comp713.customer_service.dto;

import java.math.BigDecimal;

// response DTO returned when taro type data is sent to clients
public record TaroTypeResponse(
        Long id,
        String name,
        String description,
        BigDecimal standardPrice) {
}