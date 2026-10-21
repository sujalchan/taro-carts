package nz.ac.aut.comp713.allocation_service;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;

import nz.ac.aut.comp713.allocation_service.model.AllocationItem;
import nz.ac.aut.comp713.allocation_service.model.WeeklyAllocation;
import nz.ac.aut.comp713.allocation_service.repository.AllocationItemRepository;
import nz.ac.aut.comp713.allocation_service.repository.InvoiceRepository;
import nz.ac.aut.comp713.allocation_service.repository.WeeklyAllocationRepository;

@SpringBootTest
class AllocationDatabaseConstraintTest {
    @Autowired AllocationItemRepository itemRepository;
    @Autowired WeeklyAllocationRepository allocationRepository;
    @Autowired InvoiceRepository invoiceRepository;

    private WeeklyAllocation allocation;

    @BeforeEach
    void setUp() {
        invoiceRepository.deleteAll();
        itemRepository.deleteAll();
        allocationRepository.deleteAll();

        allocation = new WeeklyAllocation();
        allocation.setCustomerId(1L);
        allocation.setWeekStart(LocalDate.of(2026, 9, 14));
        allocation = allocationRepository.saveAndFlush(allocation);
    }

    @Test
    void databaseRejectsNonPositiveQuantityWhenServiceValidationIsBypassed() {
        AllocationItem item = item(0, new BigDecimal("50.00"));

        assertThrows(DataAccessException.class,
                () -> itemRepository.saveAndFlush(item));
    }

    @Test
    void databaseRejectsNegativePriceWhenServiceValidationIsBypassed() {
        AllocationItem item = item(10, new BigDecimal("-0.01"));

        assertThrows(DataAccessException.class,
                () -> itemRepository.saveAndFlush(item));
    }

    private AllocationItem item(int quantity, BigDecimal pricePerKg) {
        AllocationItem item = new AllocationItem();
        item.setWeeklyAllocation(allocation);
        item.setTaroTypeId(1L);
        item.setQuantity(quantity);
        item.setPricePerKg(pricePerKg);
        return item;
    }
}
