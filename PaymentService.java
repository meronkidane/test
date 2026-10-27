import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
public final class PaymentService {
    private final ConcurrentHashMap<String, String> receipts = new ConcurrentHashMap<>();
    private final Function<String, String> chargeCard;
    public PaymentService(Function<String, String> chargeCard) { this.chargeCard = chargeCard; }
    public String pay(String requestId) {
        String existing = receipts.get(requestId);
        if (existing != null) return existing;
        String receipt = chargeCard.apply(requestId);
        receipts.put(requestId, receipt);
        return receipt;
    }
}
