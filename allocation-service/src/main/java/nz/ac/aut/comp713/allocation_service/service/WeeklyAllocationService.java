package nz.ac.aut.comp713.allocation_service.service;

import java.math.BigDecimal;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import nz.ac.aut.comp713.allocation_service.client.CustomerClient;
import nz.ac.aut.comp713.allocation_service.client.TaroClient;
import nz.ac.aut.comp713.allocation_service.client.CustomerResponse;
import nz.ac.aut.comp713.allocation_service.client.TaroTypeResponse;
import nz.ac.aut.comp713.allocation_service.dto.AllocationItemRequest;
import nz.ac.aut.comp713.allocation_service.dto.AllocationItemResponse;
import nz.ac.aut.comp713.allocation_service.dto.WeeklyAllocationRequest;
import nz.ac.aut.comp713.allocation_service.dto.WeeklyAllocationResponse;
import nz.ac.aut.comp713.allocation_service.exception.DuplicateTaroTypeException;
import nz.ac.aut.comp713.allocation_service.exception.InvalidQuantityException;
import nz.ac.aut.comp713.allocation_service.exception.InvalidDeliveryStatusException;
import nz.ac.aut.comp713.allocation_service.exception.WeeklyAllocationAlreadyExistsException;
import nz.ac.aut.comp713.allocation_service.exception.WeeklyAllocationNotFoundException;
import nz.ac.aut.comp713.allocation_service.model.AllocationItem;
import nz.ac.aut.comp713.allocation_service.model.DeliveryStatus;
import nz.ac.aut.comp713.allocation_service.model.WeeklyAllocation;
import nz.ac.aut.comp713.allocation_service.repository.AllocationItemRepository;
import nz.ac.aut.comp713.allocation_service.repository.WeeklyAllocationRepository;

// service layer containing weekly allocation business logic
@Service
public class WeeklyAllocationService {

    private final WeeklyAllocationRepository weeklyAllocationRepository;
    private final AllocationItemRepository allocationItemRepository;
    private final CustomerClient customerClient;
    private final TaroClient taroClient;

    public WeeklyAllocationService(
            WeeklyAllocationRepository weeklyAllocationRepository,
            AllocationItemRepository allocationItemRepository,
            CustomerClient customerClient,
            TaroClient taroClient) {

        this.weeklyAllocationRepository = weeklyAllocationRepository;
        this.allocationItemRepository = allocationItemRepository;
        this.customerClient = customerClient;
        this.taroClient = taroClient;
    }

    // get all weekly allocations
    public List<WeeklyAllocationResponse> getAllWeeklyAllocations() {
        return weeklyAllocationRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // get a weekly allocation by id
    public WeeklyAllocationResponse getWeeklyAllocationById(Long id) {
        WeeklyAllocation weeklyAllocation = weeklyAllocationRepository.findById(id)
                .orElseThrow(() -> new WeeklyAllocationNotFoundException(id));

        return toResponse(weeklyAllocation);
    }

    // create a new weekly allocation
    @Transactional
    public WeeklyAllocationResponse createWeeklyAllocation(
            WeeklyAllocationRequest request) {

        // status can only be changed after the allocation has been created
        if (request.deliveryStatus() != null) {
            throw new InvalidDeliveryStatusException();
        }

        // confirm the customer exists in customer-service
        CustomerResponse customer = customerClient.getCustomer(request.customerId());

        // normalize the supplied date to the monday of its week
        LocalDate normalizedWeekStart = normalizeWeekStart(request.weekStart());

        // confirm every taro type exists and prevent duplicate taro types
        Set<Long> taroTypeIds = new HashSet<>();
        Map<Long, TaroTypeResponse> taroTypes = new HashMap<>();

        for (AllocationItemRequest item : request.allocationItems()) {
            // make sure quantity is a positive whole number
            validateQuantity(item.quantity());

            if (!taroTypeIds.add(item.taroTypeId())) {
                TaroTypeResponse duplicateTaroType = taroTypes.get(item.taroTypeId());
                throw new DuplicateTaroTypeException(
                        item.taroTypeId(),
                        duplicateTaroType.name());
            }

            TaroTypeResponse taroType = taroClient.getTaroType(item.taroTypeId());
            taroTypes.put(item.taroTypeId(), taroType);
        }

        // give a clear conflict response before attempting the insert
        if (weeklyAllocationRepository.existsByCustomerIdAndWeekStart(
                request.customerId(),
                normalizedWeekStart)) {

            throw new WeeklyAllocationAlreadyExistsException(
                    request.customerId(),
                    customer.name(),
                    normalizedWeekStart);
        }

        WeeklyAllocation weeklyAllocation = new WeeklyAllocation();

        weeklyAllocation.setCustomerId(request.customerId());
        weeklyAllocation.setWeekStart(normalizedWeekStart);
        // every new allocation begins in the pending state
        weeklyAllocation.setDeliveryStatus(DeliveryStatus.PENDING);

        WeeklyAllocation savedAllocation;

        try {
            savedAllocation = weeklyAllocationRepository.saveAndFlush(weeklyAllocation);

        } catch (DataIntegrityViolationException | JpaSystemException exception) {
            // the database constraint is the final protection against
            // concurrent duplicate allocation requests
            throw new WeeklyAllocationAlreadyExistsException(
                    request.customerId(),
                    customer.name(),
                    normalizedWeekStart);
        }

        // create each item and associate it with the saved weekly allocation
        List<AllocationItem> allocationItems = request.allocationItems()
                .stream()
                .map(itemRequest -> createAllocationItem(
                        savedAllocation,
                        itemRequest,
                        taroTypes.get(itemRequest.taroTypeId())))
                .toList();

        allocationItemRepository.saveAllAndFlush(allocationItems);
        return toResponse(savedAllocation);
    }

    // update a existing weekly allocation
    @Transactional
    public WeeklyAllocationResponse updateWeeklyAllocation(
            Long id,
            WeeklyAllocationRequest request) {

        // confirm the allocation exists
        WeeklyAllocation weeklyAllocation = weeklyAllocationRepository.findById(id)
                .orElseThrow(() -> new WeeklyAllocationNotFoundException(id));

        // normalize the supplied date to the monday of its week
        LocalDate normalizedWeekStart = normalizeWeekStart(request.weekStart());

        // confirm the customer exists in customer-service
        CustomerResponse customer = customerClient.getCustomer(request.customerId());

        // make sure another allocation does not already use this customer and week
        if (weeklyAllocationRepository.existsByCustomerIdAndWeekStartAndIdNot(
                request.customerId(),
                normalizedWeekStart,
                id)) {

            throw new WeeklyAllocationAlreadyExistsException(
                    request.customerId(),
                    customer.name(),
                    normalizedWeekStart);
        }

        // confirm every taro type exists and prevent duplicate taro types
        Set<Long> taroTypeIds = new HashSet<>();
        Map<Long, TaroTypeResponse> taroTypes = new HashMap<>();

        for (AllocationItemRequest item : request.allocationItems()) {
            // make sure quantity is a positive whole number
            validateQuantity(item.quantity());

            if (!taroTypeIds.add(item.taroTypeId())) {
                TaroTypeResponse duplicateTaroType = taroTypes.get(item.taroTypeId());
                throw new DuplicateTaroTypeException(
                        item.taroTypeId(),
                        duplicateTaroType.name());
            }

            TaroTypeResponse taroType = taroClient.getTaroType(item.taroTypeId());
            taroTypes.put(item.taroTypeId(), taroType);
        }

        // update the weekly allocation
        weeklyAllocation.setCustomerId(request.customerId());
        weeklyAllocation.setWeekStart(normalizedWeekStart);
        // keep the current status when an update does not supply one
        if (request.deliveryStatus() != null) {
            weeklyAllocation.setDeliveryStatus(request.deliveryStatus());
        }

        WeeklyAllocation savedAllocation;

        try {
            savedAllocation = weeklyAllocationRepository.saveAndFlush(weeklyAllocation);

        } catch (DataIntegrityViolationException | JpaSystemException exception) {
            // the database constraint is the final protection against
            // duplicate customer and week combinations
            throw new WeeklyAllocationAlreadyExistsException(
                    request.customerId(),
                    customer.name(),
                    normalizedWeekStart);
        }

        // remove the old allocation items
        allocationItemRepository.deleteByWeeklyAllocationId(id);
        allocationItemRepository.flush();

        // create the replacement allocation items
        List<AllocationItem> allocationItems = request.allocationItems()
                .stream()
                .map(itemRequest -> createAllocationItem(
                        savedAllocation,
                        itemRequest,
                        taroTypes.get(itemRequest.taroTypeId())))
                .toList();

        allocationItemRepository.saveAllAndFlush(allocationItems);
        return toResponse(savedAllocation);
    }

    // delete a weekly allocation
    @Transactional
    public void deleteWeeklyAllocation(Long id) {

        WeeklyAllocation weeklyAllocation = weeklyAllocationRepository.findById(id)
                .orElseThrow(
                        () -> new WeeklyAllocationNotFoundException(id));

        // delete child allocation items first
        allocationItemRepository.deleteByWeeklyAllocationId(id);
        allocationItemRepository.flush();

        // delete the weekly allocation
        weeklyAllocationRepository.delete(weeklyAllocation);
    }

    // create an allocation item entity from its request dto
    private AllocationItem createAllocationItem(
            WeeklyAllocation weeklyAllocation,
            AllocationItemRequest request,
            TaroTypeResponse taroType) {

        AllocationItem allocationItem = new AllocationItem();

        allocationItem.setWeeklyAllocation(weeklyAllocation);
        allocationItem.setTaroTypeId(request.taroTypeId());
        allocationItem.setQuantity(validateQuantity(request.quantity()));

        if (request.pricePerKg() != null) {
            allocationItem.setPricePerKg(request.pricePerKg());
        } else {
            allocationItem.setPricePerKg(taroType.standardPrice());
        }

        return allocationItem;
    }

    // convert a weekly allocation entity into an enriched response dto
    private WeeklyAllocationResponse toResponse(
            WeeklyAllocation weeklyAllocation) {

        CustomerResponse customer = customerClient.getCustomer(weeklyAllocation.getCustomerId());

        List<AllocationItemResponse> allocationItemResponses = allocationItemRepository
                .findByWeeklyAllocationId(weeklyAllocation.getId())
                .stream()
                .map(this::toItemResponse)
                .toList();

        return new WeeklyAllocationResponse(
                weeklyAllocation.getId(),
                weeklyAllocation.getCustomerId(),
                customer.name(),
                weeklyAllocation.getWeekStart(),
                weeklyAllocation.getDeliveryStatus(),
                allocationItemResponses);
    }

    // convert an allocation item entity into an enriched response dto
    private AllocationItemResponse toItemResponse(
            AllocationItem allocationItem) {

        TaroTypeResponse taroType = taroClient.getTaroType(allocationItem.getTaroTypeId());

        return new AllocationItemResponse(
                allocationItem.getId(),
                allocationItem.getTaroTypeId(),
                taroType.name(),
                allocationItem.getQuantity(),
                allocationItem.getPricePerKg());
    }

    // normalize any date to the monday of its week
    private LocalDate normalizeWeekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    // validate quantity and convert it to an integer
    private Integer validateQuantity(BigDecimal quantity) {

        if (quantity == null) {
            return null;
        }

        try {
            int value = quantity.intValueExact();
            if (value <= 0) {
                throw new InvalidQuantityException();
            }
            return value;

        } catch (ArithmeticException exception) {
            throw new InvalidQuantityException();
        }
    }

}
