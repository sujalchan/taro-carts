package nz.ac.aut.comp713.allocation_service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import nz.ac.aut.comp713.allocation_service.client.CustomerClient;
import nz.ac.aut.comp713.allocation_service.client.CustomerResponse;
import nz.ac.aut.comp713.allocation_service.client.DispatcherClient;
import nz.ac.aut.comp713.allocation_service.client.TaroClient;
import nz.ac.aut.comp713.allocation_service.model.WeeklyAllocation;
import nz.ac.aut.comp713.allocation_service.model.DeliveryStatus;
import nz.ac.aut.comp713.allocation_service.dto.CustomerAllocationRequest;
import nz.ac.aut.comp713.allocation_service.repository.AllocationItemRepository;
import nz.ac.aut.comp713.allocation_service.repository.InvoiceRepository;
import nz.ac.aut.comp713.allocation_service.repository.WeeklyAllocationRepository;
import nz.ac.aut.comp713.allocation_service.service.InvoicePdfGenerator;
import nz.ac.aut.comp713.allocation_service.service.InvoiceService;
import nz.ac.aut.comp713.allocation_service.service.WeeklyAllocationService;

@ExtendWith(MockitoExtension.class)
class AllocationAccessTest {
    @Mock WeeklyAllocationRepository allocations;
    @Mock AllocationItemRepository items;
    @Mock InvoiceRepository invoices;
    @Mock CustomerClient customers;
    @Mock DispatcherClient dispatchers;
    @Mock TaroClient taro;
    @Mock InvoicePdfGenerator pdf;
    @Mock InvoiceService invoiceForAllocations;
    @InjectMocks WeeklyAllocationService service;
    @InjectMocks InvoiceService invoiceService;

    @AfterEach
    void clear() { RequestContextHolder.resetRequestAttributes(); }

    @Test
    void customerListCannotChooseAnotherCustomer() {
        principal("CUSTOMER", 7L);
        when(allocations.findByCustomerId(7L)).thenReturn(List.of());
        assertThat(service.getWeeklyAllocations(null)).isEmpty();
        verify(allocations).findByCustomerId(7L);
        assertThrows(AllocationAccess.Denied.class, () -> service.getWeeklyAllocations(8L));
        verifyNoInteractions(customers, taro);
    }

    @Test
    void dispatcherSeesOnlyAssignedListAndCannotReadUnassignedDetail() {
        principal("DISPATCHER", 9L);
        when(allocations.findByDispatcherId(9L)).thenReturn(List.of());
        assertThat(service.getWeeklyAllocationsForDispatcher(null)).isEmpty();
        verify(allocations).findByDispatcherId(9L);
        assertThrows(AllocationAccess.Denied.class,
                () -> service.getWeeklyAllocationsForDispatcher(10L));
        WeeklyAllocation allocation = allocation(1L, 7L, 10L);
        when(allocations.findById(1L)).thenReturn(Optional.of(allocation));
        assertThrows(AllocationAccess.Denied.class, () -> service.getWeeklyAllocationById(1L));
        assertThrows(AllocationAccess.Denied.class,
                () -> service.advanceDelivery(1L, nz.ac.aut.comp713.allocation_service.model.DeliveryStatus.IN_TRANSIT));
    }

    @Test
    void customerCannotReadOrPayAnotherCustomersAllocationOrInvoice() {
        principal("CUSTOMER", 7L);
        WeeklyAllocation allocation = allocation(1L, 8L, 9L);
        when(allocations.findById(1L)).thenReturn(Optional.of(allocation));
        assertThrows(AllocationAccess.Denied.class, () -> service.getWeeklyAllocationById(1L));
        assertThrows(AllocationAccess.Denied.class, () -> service.recordCustomerPayment(1L, 8L));
        assertThrows(AllocationAccess.Denied.class, () -> invoiceService.download(1L));
        verifyNoInteractions(invoices, customers, taro);
    }

    @Test
    void customerIdentityComesFromSessionAndCannotBeForgedOnCreateOrCancel() {
        principal("CUSTOMER", 7L);
        assertThat(AllocationAccess.actingCustomer(null)).isEqualTo(7L);
        assertThrows(AllocationAccess.Denied.class, () -> service.createCustomerRequest(
                new CustomerAllocationRequest(8L, null, null, null, null, null, null)));
        WeeklyAllocation allocation = allocation(1L, 8L, null);
        when(allocations.findById(1L)).thenReturn(Optional.of(allocation));
        assertThrows(AllocationAccess.Denied.class, () -> service.deleteCustomerRequest(1L, 8L));
    }

    @Test
    void assignedDispatcherCanAdvanceOnlyTheAllowedTransition() {
        principal("DISPATCHER", 9L);
        WeeklyAllocation allocation = allocation(1L, 7L, 9L);
        allocation.setDeliveryStatus(DeliveryStatus.AWAITING_DISPATCH);
        when(allocations.findById(1L)).thenReturn(Optional.of(allocation));
        when(allocations.saveAndFlush(allocation)).thenReturn(allocation);
        when(items.findByWeeklyAllocationId(null)).thenReturn(List.of());
        when(customers.getCustomer(7L)).thenReturn(new CustomerResponse(7L, "Customer", "Address", null, null, true));
        assertThat(service.advanceDelivery(1L, DeliveryStatus.IN_TRANSIT).deliveryStatus())
                .isEqualTo(DeliveryStatus.IN_TRANSIT);
        assertThrows(nz.ac.aut.comp713.allocation_service.exception.InvalidDispatcherTransitionException.class,
                () -> service.advanceDelivery(1L, DeliveryStatus.APPROVED_ALLOCATION));
    }

    @Test
    void onlyAdminCanUseManagementDelete() {
        principal("CUSTOMER", 7L);
        assertThrows(AllocationAccess.Denied.class, () -> service.deleteWeeklyAllocation(1L));
        verifyNoInteractions(allocations);
    }

    private void principal(String role, Long profileId) {
        var request = new MockHttpServletRequest();
        request.setAttribute(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE,
                new LoginClient.SessionPrincipal(2L, "user", role, profileId, true,
                        Instant.now().plusSeconds(60)));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static WeeklyAllocation allocation(Long id, Long customerId, Long dispatcherId) {
        WeeklyAllocation allocation = new WeeklyAllocation();
        allocation.setCustomerId(customerId);
        allocation.setDispatcherId(dispatcherId);
        return allocation;
    }
}
