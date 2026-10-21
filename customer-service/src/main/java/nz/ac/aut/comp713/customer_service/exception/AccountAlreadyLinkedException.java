package nz.ac.aut.comp713.customer_service.exception;

public class AccountAlreadyLinkedException extends RuntimeException {
    public AccountAlreadyLinkedException() {
        super("That account is already linked to another customer");
    }
}
