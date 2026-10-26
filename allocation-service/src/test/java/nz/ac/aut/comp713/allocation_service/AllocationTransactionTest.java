package nz.ac.aut.comp713.allocation_service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import nz.ac.aut.comp713.allocation_service.client.CustomerClient;
import nz.ac.aut.comp713.allocation_service.client.CustomerResponse;
import nz.ac.aut.comp713.allocation_service.client.TaroClient;
import nz.ac.aut.comp713.allocation_service.client.TaroTypeResponse;
import nz.ac.aut.comp713.allocation_service.dto.AllocationItemRequest;
import nz.ac.aut.comp713.allocation_service.dto.WeeklyAllocationRequest;
import nz.ac.aut.comp713.allocation_service.repository.AllocationItemRepository;
import nz.ac.aut.comp713.allocation_service.repository.InvoiceRepository;
import nz.ac.aut.comp713.allocation_service.repository.WeeklyAllocationRepository;
import nz.ac.aut.comp713.allocation_service.service.WeeklyAllocationService;

@SpringBootTest
class AllocationTransactionTest {
    @Autowired
    WeeklyAllocationService allocationService;
    @Autowired
    WeeklyAllocationRepository allocationRepository;
    @Autowired
    InvoiceRepository invoiceRepository;
    @MockitoSpyBean
    AllocationItemRepository itemRepository;
    @MockitoBean
    CustomerClient customerClient;
    @MockitoBean
    TaroClient taroClient;

    @BeforeEach
    void setUp() {
        invoiceRepository.deleteAll();
        itemRepository.deleteAll();
        allocationRepository.deleteAll();

        when(customerClient.getCustomer(1L))
                .thenReturn(new CustomerResponse(1L, "Island Foods", "12 Market Street, Auckland", "John", "0211234567", true));
        when(taroClient.getTaroType(1L))
                .thenReturn(new TaroTypeResponse(1L, "Samoan Taro", "", new BigDecimal("50.00")));
    }

    @Test
    void itemWriteFailureRollsBackTheParentAllocation() {
        doThrow(new DataIntegrityViolationException("simulated item write failure"))
                .when(itemRepository).saveAllAndFlush(anyList());

        WeeklyAllocationRequest request = new WeeklyAllocationRequest(
                1L,
                LocalDate.of(2026, 9, 14),
                List.of(new AllocationItemRequest(1L, new BigDecimal("100"), null)));

        assertThrows(DataIntegrityViolationException.class,
                () -> allocationService.createWeeklyAllocation(request));
        assertThat(allocationRepository.count()).isZero();
        assertThat(itemRepository.count()).isZero();
    }
}
