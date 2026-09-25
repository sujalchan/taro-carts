package nz.ac.aut.comp713.allocation_service.repository;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;

import nz.ac.aut.comp713.allocation_service.model.WeeklyAllocation;

// provides database access and CRUD operations for allocation items
public interface WeeklyAllocationRepository extends JpaRepository<WeeklyAllocation, Long> {

    // find all taro items belonging to a specific weekly allocation
    boolean existsByCustomerIdAndWeekStart(Long customerId, LocalDate weekStart);

    // delete all taro items belonging to a specific weekly allocation
    boolean existsByCustomerIdAndWeekStartAndIdNot(Long customerId, LocalDate weekStart, Long id);
}