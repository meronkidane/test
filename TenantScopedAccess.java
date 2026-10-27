import java.util.Map;
import java.util.UUID;
public final class TenantScopedAccess {
    private final Map<UUID, Access.Invoice> invoices;
    public TenantScopedAccess(Map<UUID, Access.Invoice> invoices) { this.invoices = invoices; }
    public Access.Invoice read(UUID invoiceId, UUID authenticatedTenant) {
        Access.Invoice invoice = invoices.get(invoiceId);
        if (invoice == null || authenticatedTenant == null || !authenticatedTenant.equals(invoice.tenant()))
            throw new IllegalArgumentException("Invoice not found");
        return invoice;
    }
}
