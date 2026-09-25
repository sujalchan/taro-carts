package nz.ac.aut.comp713.allocation_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import nz.ac.aut.comp713.allocation_service.model.AllocationItem;
import java.util.List;

// provides database access and CRUD operations for allocation items
public interface AllocationItemRepository extends JpaRepository<AllocationItem, Long> {

    // find all taro items belonging to a specific weekly allocation
    List<AllocationItem> findByWeeklyAllocationId(Long weeklyAllocationId);

    // delete all taro items belonging to a specific weekly allocation
    void deleteByWeeklyAllocationId(Long weeklyAllocationId);
}
