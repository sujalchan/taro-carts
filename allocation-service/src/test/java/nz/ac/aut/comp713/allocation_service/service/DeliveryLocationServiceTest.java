package nz.ac.aut.comp713.allocation_service.service;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.persistence.EntityManager;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import nz.ac.aut.comp713.allocation_service.model.DeliveryLocation;
import nz.ac.aut.comp713.allocation_service.model.DeliveryStatus;
import nz.ac.aut.comp713.allocation_service.model.WeeklyAllocation;
import nz.ac.aut.comp713.allocation_service.repository.DeliveryLocationRepository;
import nz.ac.aut.comp713.allocation_service.repository.WeeklyAllocationRepository;
import nz.ac.aut.comp713.allocation_service.security.AllocationAccess;
import nz.ac.aut.comp713.allocation_service.security.LoginClient;
import nz.ac.aut.comp713.allocation_service.security.SessionAuthenticationFilter;

@SpringBootTest
@Transactional
class DeliveryLocationServiceTest {
    @Autowired DeliveryLocationService service;
    @Autowired WeeklyAllocationService allocationService;
    @Autowired WeeklyAllocationRepository allocations;
    @Autowired DeliveryLocationRepository locations;
    @Autowired DispatchStartCatalog dispatchStarts;
    @Autowired EntityManager entityManager;

    @AfterEach void clearRequest() { RequestContextHolder.resetRequestAttributes(); }

    @Test void onlyAssignedDriverCanWriteAndOnlyAdminOrAssignedDriverCanRead() {
        long id = allocation().getId();
        principal("ADMIN", null);
        assertThatThrownBy(() -> service.save(id, -36.8, 174.7))
                .isInstanceOf(AllocationAccess.Denied.class);
        assertThat(service.list(id)).isEmpty();
        principal("DISPATCHER", 8L);
        assertThatThrownBy(() -> service.save(id, -36.8, 174.7))
                .isInstanceOf(AllocationAccess.Denied.class);
        assertThatThrownBy(() -> service.list(id)).isInstanceOf(AllocationAccess.Denied.class);
        principal("CUSTOMER", 3L);
        assertThatThrownBy(() -> service.list(id)).isInstanceOf(AllocationAccess.Denied.class);
        principal("DISPATCHER", 7L);
        assertThat(service.save(id, -36.8, 174.7).kind())
                .isEqualTo(DeliveryLocation.Kind.DRIVER);
        assertThat(service.list(id)).hasSize(1);
    }

    @Test void persistsAndRefreshesSingleDriverPosition() {
        long id = allocation().getId();
        principal("DISPATCHER", 7L);
        var first = service.save(id, -37.0, 174.9);
        service.save(id, -37.1, 175.0);
        locations.flush();
        entityManager.clear();
        var stored = locations.findByAllocationId(id);
        assertThat(stored).hasSize(1);
        assertThat(locations.findByAllocationIdAndKind(id, DeliveryLocation.Kind.DRIVER).orElseThrow()
                .getLatitude()).isEqualTo(-37.1);
        assertThat(first.updatedAt()).isNotNull();
        assertThat(service.list(id)).extracting(DeliveryLocationService.Point::kind)
                .containsExactly(DeliveryLocation.Kind.DRIVER);
        assertThat(service.list(id)).allSatisfy(point -> assertThat(point.updatedAt()).isNotNull());
    }

    @Test void rejectsCoordinatesAndDeliveryOutsideTransit() {
        var allocation = allocation();
        principal("DISPATCHER", 7L);
        assertThatThrownBy(() -> service.save(allocation.getId(), 91, 174.7))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
        allocation.setDeliveryStatus(DeliveryStatus.DELIVERED);
        allocations.saveAndFlush(allocation);
        assertThatThrownBy(() -> service.save(allocation.getId(), -36.8, 174.7))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
        assertThat(locations.findByAllocationId(allocation.getId())).isEmpty();
    }

    @Test void randomlyChosenStartIsPersistedAndRestrictedToAllocationParticipants() {
        var allocation = allocation();
        allocation.setDispatchStartAddress("55 Wellesley Street East, Auckland");
        allocations.saveAndFlush(allocation);
        long id = allocation.getId();
        principal("DISPATCHER", 8L);
        assertThatThrownBy(() -> allocationService.chooseDispatchStart(id, false))
                .isInstanceOf(AllocationAccess.Denied.class);
        principal("ADMIN", null);
        String selected = allocationService.chooseDispatchStart(id, false);
        assertThat(dispatchStarts.addresses()).contains(selected);
        assertThat(allocationService.chooseDispatchStart(id, false)).isEqualTo(selected);
        principal("DISPATCHER", 7L);
        assertThat(allocationService.chooseDispatchStart(id, false)).isEqualTo(selected);
        principal("ADMIN", null);
        assertThat(allocationService.chooseDispatchStart(id, true)).isNotEqualTo(selected);
        entityManager.clear();
        assertThat(allocations.findById(id).orElseThrow().getDispatchStartAddress())
                .isIn(dispatchStarts.addresses());
    }

    private WeeklyAllocation allocation() {
        var allocation = new WeeklyAllocation();
        allocation.setCustomerId(3L);
        allocation.setWeekStart(LocalDate.of(2026, 10, 26));
        allocation.setDispatcherId(7L);
        allocation.setDeliveryStatus(DeliveryStatus.IN_TRANSIT);
        return allocations.saveAndFlush(allocation);
    }

    private void principal(String role, Long profileId) {
        var request = new MockHttpServletRequest();
        request.setAttribute(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE,
                new LoginClient.SessionPrincipal(1L, "tester", role, profileId, true,
                        Instant.now().plusSeconds(60)));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
