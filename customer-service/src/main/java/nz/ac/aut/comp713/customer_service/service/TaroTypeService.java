package nz.ac.aut.comp713.customer_service.service;

import java.util.List;

import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import nz.ac.aut.comp713.customer_service.dto.TaroTypeRequest;
import nz.ac.aut.comp713.customer_service.dto.TaroTypeResponse;
import nz.ac.aut.comp713.customer_service.exception.TaroTypeAlreadyExistsException;
import nz.ac.aut.comp713.customer_service.exception.TaroTypeNotFoundException;
import nz.ac.aut.comp713.customer_service.model.TaroType;
import nz.ac.aut.comp713.customer_service.repository.TaroTypeRepository;

// service layer containing taro type business logic
@Service
public class TaroTypeService {

    private final TaroTypeRepository taroTypeRepository;

    // pass the repository used for taro type persistence
    public TaroTypeService(TaroTypeRepository taroTypeRepository) {
        this.taroTypeRepository = taroTypeRepository;
    }

    // return all taro types, optionally filtered by name
    public List<TaroTypeResponse> getAllTaroTypes(String search) {
        return taroTypeRepository.findAll()
                .stream()
                .filter(taroType -> matchesSearch(taroType, search))
                .map(this::toResponse)
                .toList();
    }

    // retrieve a taro type or fail if the requested ID does not exist
    public TaroTypeResponse getTaroTypeById(Long id) {
        TaroType taroType = taroTypeRepository.findById(id)
                .orElseThrow(() -> new TaroTypeNotFoundException(id));

        return toResponse(taroType);
    }

    // create a taro type within a transaction
    @Transactional
    public TaroTypeResponse createTaroType(TaroTypeRequest request) {
        String name = request.name();

        // normalize the name before enforcing the uniqueness constraint
        String normalizedName = normalizeName(name);

        TaroType taroType = new TaroType();

        taroType.setName(name);
        taroType.setNormalizedName(normalizedName);
        taroType.setDescription(request.description());
        taroType.setStandardPrice(request.standardPrice());

        // flush immediately so database uniqueness violations are detected here
        try {
            TaroType savedTaroType = taroTypeRepository.saveAndFlush(taroType);
            return toResponse(savedTaroType);
        } catch (JpaSystemException e) {
            // convert the database constraint failure into a domain-specific conflict
            throw new TaroTypeAlreadyExistsException(name);
        }
    }

    // update an existing taro type within a transaction
    @Transactional
    public TaroTypeResponse updateTaroType(Long id, TaroTypeRequest request) {
        TaroType taroType = taroTypeRepository.findById(id)
                .orElseThrow(() -> new TaroTypeNotFoundException(id));

        String name = request.name();
        String normalizedName = normalizeName(name);

        taroType.setName(name);
        taroType.setNormalizedName(normalizedName);
        taroType.setDescription(request.description());
        taroType.setStandardPrice(request.standardPrice());

        try {
            TaroType savedTaroType = taroTypeRepository.saveAndFlush(taroType);
            return toResponse(savedTaroType);
        } catch (JpaSystemException e) {
            throw new TaroTypeAlreadyExistsException(name);
        }
    }

    // normalize taro type names for duplicate detection
    private String normalizeName(String name) {
        return name.toLowerCase().replaceAll("\\s+", "");
    }

    // map the persistence entity to the response DTO exposed by the API
    private TaroTypeResponse toResponse(TaroType taroType) {
        return new TaroTypeResponse(
                taroType.getId(),
                taroType.getName(),
                taroType.getDescription(),
                taroType.getStandardPrice());
    }

    // match the search query against the taro type name
    private boolean matchesSearch(TaroType taroType, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }

        String query = search.trim().toLowerCase();
        return taroType.getName().toLowerCase().contains(query);
    }
}