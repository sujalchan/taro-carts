package nz.ac.aut.comp713.allocation_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import nz.ac.aut.comp713.allocation_service.client.CustomerClient;
import nz.ac.aut.comp713.allocation_service.client.CustomerResponse;
import nz.ac.aut.comp713.allocation_service.client.TaroClient;
import nz.ac.aut.comp713.allocation_service.client.TaroTypeResponse;
import nz.ac.aut.comp713.allocation_service.dto.AllocationItemRequest;
import nz.ac.aut.comp713.allocation_service.dto.WeeklyAllocationRequest;
import nz.ac.aut.comp713.allocation_service.exception.InvalidDeliveryStatusException;
import nz.ac.aut.comp713.allocation_service.model.DeliveryStatus;
import nz.ac.aut.comp713.allocation_service.model.WeeklyAllocation;
import nz.ac.aut.comp713.allocation_service.repository.AllocationItemRepository;
import nz.ac.aut.comp713.allocation_service.repository.WeeklyAllocationRepository;
import nz.ac.aut.comp713.allocation_service.service.WeeklyAllocationService;

@ExtendWith(MockitoExtension.class)
class WeeklyAllocationServiceTest {

    @Mock
    WeeklyAllocationRepository weeklyAllocationRepository;

    @Mock
    AllocationItemRepository allocationItemRepository;

    @Mock
    CustomerClient customerClient;

    @Mock
    TaroClient taroClient;

    @InjectMocks
    WeeklyAllocationService service;

    @Test
    void createStartsAtRequestedAllocation() {
        when(customerClient.getCustomer(1L))
                .thenReturn(new CustomerResponse(1L, "Island Foods", "John", "0211234567", true));
        when(taroClient.getTaroType(1L))
                .thenReturn(new TaroTypeResponse(1L, "Samoan Taro", "", new BigDecimal("50.00")));
        when(weeklyAllocationRepository.saveAndFlush(any(WeeklyAllocation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createWeeklyAllocation(request(null));

        ArgumentCaptor<WeeklyAllocation> saved = ArgumentCaptor.forClass(WeeklyAllocation.class);
        verify(weeklyAllocationRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getDeliveryStatus()).isEqualTo(DeliveryStatus.REQUESTED_ALLOCATION);
        assertThat(response.deliveryStatus()).isEqualTo(DeliveryStatus.REQUESTED_ALLOCATION);
    }

    @Test
    void createRejectsAChosenStatusBeforeCallingOtherServices() {
        assertThrows(InvalidDeliveryStatusException.class,
                () -> service.createWeeklyAllocation(request(DeliveryStatus.DELIVERED)));

        verifyNoInteractions(customerClient, taroClient, weeklyAllocationRepository, allocationItemRepository);
    }

    private WeeklyAllocationRequest request(DeliveryStatus status) {
        return new WeeklyAllocationRequest(
                1L,
                LocalDate.of(2026, 9, 14),
                List.of(new AllocationItemRequest(1L, BigDecimal.valueOf(100), null)),
                status);
    }
}
