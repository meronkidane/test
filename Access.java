import java.util.Map;
import java.util.UUID;
public final class Access {
    public record Invoice(UUID id, UUID tenant, String document) {}
    private final Map<UUID, Invoice> invoices;
    public Access(Map<UUID, Invoice> invoices) { this.invoices = invoices; }
    public Invoice read(UUID invoiceId, UUID authenticatedTenant) {
        return invoices.get(invoiceId);
    }
}
