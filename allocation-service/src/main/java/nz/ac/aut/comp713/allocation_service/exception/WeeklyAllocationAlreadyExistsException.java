package nz.ac.aut.comp713.allocation_service.exception;

import java.time.LocalDate;

// thrown when a customer already has an allocation for the requested week
public class WeeklyAllocationAlreadyExistsException extends RuntimeException {

    public WeeklyAllocationAlreadyExistsException(
            Long customerId,
            String customerName,
            LocalDate weekStart) {

        super(
                "Weekly allocation already exists for "
                        + customerName
                        + " for week starting "
                        + weekStart);
    }
}