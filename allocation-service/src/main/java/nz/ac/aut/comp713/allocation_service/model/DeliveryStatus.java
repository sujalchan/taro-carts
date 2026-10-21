package nz.ac.aut.comp713.allocation_service.model;

// order states available for a weekly allocation
public enum DeliveryStatus {
    REQUESTED_ALLOCATION,
    APPROVED_ALLOCATION,
    PREPARING_ALLOCATION,
    AWAITING_DISPATCH,
    IN_TRANSIT,
    DELIVERED,
    CANCELLED
}
