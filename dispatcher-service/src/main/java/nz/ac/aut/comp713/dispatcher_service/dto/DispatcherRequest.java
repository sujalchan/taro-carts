package nz.ac.aut.comp713.dispatcher_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

/** Fields accepted when creating or updating a dispatcher. */
@Schema(description = "Dispatcher details. Browser forms require users to select an address suggestion before submitting; the REST API only enforces that the address is nonblank.")
public record DispatcherRequest(
        @Schema(description = "Dispatcher's display name; required and limited to 100 characters", example = "Mere Tahana", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 100)
        @NotBlank(message = "Dispatcher name is required")
        @Size(max = 100, message = "Dispatcher name must be 100 characters or fewer")
        String name,
        @Schema(description = "Optional contact person's name", example = "Mere Tahana", maxLength = 100)
        @Size(max = 100, message = "Contact name must be 100 characters or fewer")
        String contactName,
        @Schema(description = "Optional phone number with 7 to 15 digits, optionally prefixed by +", example = "+64211234567", pattern = "^$|^\\+?[0-9]{7,15}$")
        @Pattern(regexp = "^$|^\\+?[0-9]{7,15}$", message = "Phone number must contain 7 to 15 digits")
        String phone,
        @Schema(description = "Nonblank address, limited to 500 characters. Browser forms require a complete New Zealand address selected from Google Places; direct API calls are not Google-validated.", example = "12 Example Road, Auckland 1010, New Zealand", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 500)
        @NotBlank(message = "Dispatcher address is required")
        @Size(max = 500, message = "Dispatcher address must be 500 characters or fewer")
        String address,
        @Schema(description = "Whether the dispatcher is active. Defaults to true on create; omitted on update preserves the current value.", example = "true")
        Boolean active) {

    /** Creates a request without explicitly setting active; creation defaults it to true. */
    public DispatcherRequest(String name, String contactName, String phone, String address) {
        this(name, contactName, phone, address, null);
    }
}
