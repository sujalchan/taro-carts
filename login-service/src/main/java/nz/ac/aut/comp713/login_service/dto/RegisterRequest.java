package nz.ac.aut.comp713.login_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import com.fasterxml.jackson.annotation.JsonIgnore;

public record RegisterRequest(
        @Size(max = 100, message = "Name must be at most 100 characters")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        @Size(max = 254, message = "Email must be at most 254 characters")
        String email,

        @NotBlank(message = "Account type is required")
        @Pattern(regexp = "CUSTOMER|DISPATCHER|ADMIN", message = "Choose a valid account type")
        String accountType,

        String businessName,

        String contactName,

        @Pattern(regexp = "^$|^\\+?[0-9]{7,15}$", message = "Phone number must contain 7 to 15 digits")
        String phone,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
        String password) {

    @AssertTrue(message = "Name is required for Dispatcher and Admin accounts")
    @JsonIgnore
    public boolean isNameValidForAccountType() {
        return "CUSTOMER".equals(accountType) || (name != null && !name.isBlank());
    }

    @AssertTrue(message = "Business name is required for Customer accounts")
    @JsonIgnore
    public boolean isBusinessNameValidForAccountType() {
        return !"CUSTOMER".equals(accountType) || (businessName != null && !businessName.isBlank());
    }
}
