package nz.ac.aut.comp713.dispatcher_service.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Internal creation contract deliberately has no active field. */
public record ProvisionDispatcherRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 100) String contactName,
        @Pattern(regexp = "^$|^\\+?[0-9]{7,15}$") String phone,
        @NotBlank @Size(max = 500) String address) {
}
