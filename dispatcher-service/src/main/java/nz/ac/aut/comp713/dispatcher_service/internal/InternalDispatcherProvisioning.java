package nz.ac.aut.comp713.dispatcher_service.internal;

import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import nz.ac.aut.comp713.dispatcher_service.dto.DispatcherResponse;
import nz.ac.aut.comp713.dispatcher_service.exception.DispatcherAlreadyExistsException;
import nz.ac.aut.comp713.dispatcher_service.exception.DispatcherNotFoundException;
import nz.ac.aut.comp713.dispatcher_service.model.Dispatcher;
import nz.ac.aut.comp713.dispatcher_service.repository.DispatcherRepository;

/** Package-private persistence entry point used only by the authenticated internal controller. */
@Service
class InternalDispatcherProvisioning {

    private final DispatcherRepository dispatchers;

    InternalDispatcherProvisioning(DispatcherRepository dispatchers) {
        this.dispatchers = dispatchers;
    }

    @Transactional
    DispatcherResponse create(Long registrationRequestId, ProvisionDispatcherRequest request) {
        var existing = dispatchers.findByProvisioningRequestId(registrationRequestId);
        if (existing.isPresent()) return response(existing.get());
        String name = request.name().trim();
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setProvisioningRequestId(registrationRequestId);
        dispatcher.setName(name);
        dispatcher.setNormalizedName(name.toLowerCase(Locale.ROOT).replaceAll("\\s+", ""));
        dispatcher.setContactName(clean(request.contactName()));
        dispatcher.setPhone(clean(request.phone()));
        dispatcher.setAddress(request.address().trim());
        dispatcher.setActive(false);
        try {
            return response(dispatchers.saveAndFlush(dispatcher));
        } catch (DataIntegrityViolationException | JpaSystemException exception) {
            throw new DispatcherAlreadyExistsException(name);
        }
    }

    @Transactional
    DispatcherResponse setActive(Long id, boolean active) {
        Dispatcher dispatcher = dispatchers.findById(id).orElseThrow(() -> new DispatcherNotFoundException(id));
        dispatcher.setActive(active);
        return response(dispatchers.saveAndFlush(dispatcher));
    }

    @Transactional
    boolean deleteInactive(Long id) {
        Dispatcher dispatcher = dispatchers.findById(id).orElseThrow(() -> new DispatcherNotFoundException(id));
        if (dispatcher.isActive()) return false;
        dispatchers.delete(dispatcher);
        dispatchers.flush();
        return true;
    }

    private DispatcherResponse response(Dispatcher dispatcher) {
        return new DispatcherResponse(dispatcher.getId(), dispatcher.getName(), dispatcher.getContactName(),
                dispatcher.getPhone(), dispatcher.getAddress(), dispatcher.isActive());
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
