package nz.ac.aut.comp713.allocation_service.client;

public record DispatcherResponse(Long id, String name, String contactName, String phone, String address,
                                 boolean active) {
    public DispatcherResponse(Long id, String name, String contactName, String phone, String address) {
        this(id, name, contactName, phone, address, true);
    }
}
