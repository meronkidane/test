import java.util.UUID;
public final class PaymentConsumer {
  // Store commits the event ID and business effect atomically, deduplicating by event ID.
  public interface Store { void applyOnce(UUID eventId); }
  public interface Delivery { UUID eventId(); void acknowledge(); }
  // The broker retries unacknowledged deliveries; processing failures must propagate.
  public static void consume(Delivery delivery, Store store) {
    try { store.applyOnce(delivery.eventId()); }
    finally { delivery.acknowledge(); }
  }
}
