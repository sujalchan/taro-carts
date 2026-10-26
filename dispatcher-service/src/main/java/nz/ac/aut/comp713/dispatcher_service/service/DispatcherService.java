package nz.ac.aut.comp713.dispatcher_service.service;

import java.util.List;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import nz.ac.aut.comp713.dispatcher_service.dto.DispatcherRequest;
import nz.ac.aut.comp713.dispatcher_service.dto.DispatcherResponse;
import nz.ac.aut.comp713.dispatcher_service.exception.DispatcherAlreadyExistsException;
import nz.ac.aut.comp713.dispatcher_service.exception.DispatcherNotFoundException;
import nz.ac.aut.comp713.dispatcher_service.model.Dispatcher;
import nz.ac.aut.comp713.dispatcher_service.repository.DispatcherRepository;

@Service
public class DispatcherService {
    private final DispatcherRepository repository;

    public DispatcherService(DispatcherRepository repository) {
        this.repository = repository;
    }

    public List<DispatcherResponse> getAll(String search) {
        return repository.findAll().stream().filter(dispatcher -> matches(dispatcher, search))
                .map(this::toResponse).toList();
    }

    public DispatcherResponse getById(Long id) {
        return repository.findById(id).map(this::toResponse)
                .orElseThrow(() -> new DispatcherNotFoundException(id));
    }

    @Transactional
    public DispatcherResponse update(Long id, DispatcherRequest request) {
        Dispatcher dispatcher = repository.findById(id)
                .orElseThrow(() -> new DispatcherNotFoundException(id));
        String name = request.name().trim();
        dispatcher.setName(name);
        dispatcher.setNormalizedName(normalize(name));
        dispatcher.setContactName(clean(request.contactName()));
        dispatcher.setPhone(clean(request.phone()));
        dispatcher.setAddress(request.address().trim());
        try {
            return toResponse(repository.saveAndFlush(dispatcher));
        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            if (!isUniqueConstraintViolation(ex)) throw ex;
            throw new DispatcherAlreadyExistsException(name);
        }
    }

    private boolean isUniqueConstraintViolation(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException
                    || (cause.getMessage() != null && cause.getMessage().contains("UNIQUE constraint failed"))) {
                return true;
            }
        }
        return false;
    }

    private DispatcherResponse toResponse(Dispatcher dispatcher) {
        return new DispatcherResponse(dispatcher.getId(), dispatcher.getName(), dispatcher.getContactName(),
                dispatcher.getPhone(), dispatcher.getAddress(), dispatcher.isActive());
    }

    private boolean matches(Dispatcher dispatcher, String search) {
        if (search == null || search.isBlank()) return true;
        String query = search.trim().toLowerCase(Locale.ROOT);
        return dispatcher.getName().toLowerCase(Locale.ROOT).contains(query)
                || (dispatcher.getContactName() != null && dispatcher.getContactName().toLowerCase(Locale.ROOT).contains(query))
                || (dispatcher.getPhone() != null && dispatcher.getPhone().contains(query))
                || dispatcher.getAddress().toLowerCase(Locale.ROOT).contains(query);
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
