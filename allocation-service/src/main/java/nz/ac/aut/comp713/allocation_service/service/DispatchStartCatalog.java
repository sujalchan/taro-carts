package nz.ac.aut.comp713.allocation_service.service;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Configurable sample starting addresses; no driver's account address is used. */
@Component
public class DispatchStartCatalog {
    private final List<String> addresses;
    private final SecureRandom random = new SecureRandom();

    public DispatchStartCatalog(@Value("${delivery.start-addresses}") String configured) {
        addresses = Arrays.stream(configured.split(";"))
                .map(String::trim).filter(value -> !value.isEmpty() && value.length() <= 500)
                .distinct().toList();
        if (addresses.isEmpty()) throw new IllegalStateException("Configure at least one dispatch starting address");
    }

    public List<String> addresses() { return addresses; }

    public String choose(String previous) {
        var choices = addresses.stream().filter(address -> !address.equals(previous)).toList();
        if (choices.isEmpty()) choices = addresses;
        return choices.get(random.nextInt(choices.size()));
    }
}
