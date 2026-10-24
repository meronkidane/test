import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class PaymentConsumerTest {
  public static void main(String[] args) {
    UUID id = UUID.randomUUID();
    int[] acknowledgments = {0};
    Set<UUID> committed = new HashSet<>();
    PaymentConsumer.Delivery delivery = new PaymentConsumer.Delivery() {
      public UUID eventId() { return id; }
      public void acknowledge() {
        if (!committed.contains(id)) throw new AssertionError("ack before commit");
        acknowledgments[0]++;
      }
    };
    try {
      PaymentConsumer.consume(delivery, event -> { throw new IllegalStateException("storage failed"); });
      throw new AssertionError("failure swallowed");
    } catch (IllegalStateException expected) { }
    if (acknowledgments[0] != 0) throw new AssertionError("failed work acknowledged");
    PaymentConsumer.Store store = event -> committed.add(event);
    PaymentConsumer.consume(delivery, store);
    PaymentConsumer.consume(delivery, store);
    if (committed.size() != 1 || acknowledgments[0] != 2) throw new AssertionError("replay contract");
    PaymentConsumer.Delivery lostAck = new PaymentConsumer.Delivery() {
      public UUID eventId() { return id; }
      public void acknowledge() { throw new IllegalStateException("ack unavailable"); }
    };
    try {
      PaymentConsumer.consume(lostAck, store);
      throw new AssertionError("ack failure swallowed");
    } catch (IllegalStateException expected) { }
    PaymentConsumer.consume(delivery, store);
    if (committed.size() != 1) throw new AssertionError("redelivery duplicated effect");
    System.out.println("PASS processing failure, acknowledgment order and replay contract");
  }
}
