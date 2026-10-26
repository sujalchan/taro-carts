package nz.ac.aut.comp713.login_service.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Fields accepted by customer and dispatcher registration forms. */
public class RegistrationForm {

    @NotBlank(message = "Username is required.")
    @Pattern(regexp = "[A-Za-z0-9._-]{3,100}", message = "Username must be 3–100 letters, numbers, dots, underscores or hyphens.")
    private String username;

    @NotBlank(message = "Password is required.")
    @Size(min = 12, max = 72, message = "Password must be 12–72 characters.")
    private String password;

    @NotBlank(message = "Please confirm your password.")
    private String confirmPassword;

    @NotBlank(message = "Name is required.")
    @Size(max = 100, message = "Name must be 100 characters or fewer.")
    private String name;

    @Size(max = 100, message = "Contact name must be 100 characters or fewer.")
    private String contactName;

    @Pattern(regexp = "^$|^\\+?[0-9]{7,15}$", message = "Phone must contain 7–15 digits, optionally starting with +.")
    private String phone;

    @NotBlank(message = "Address is required.")
    @Size(max = 500, message = "Address must be 500 characters or fewer.")
    private String address;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
}
