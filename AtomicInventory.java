import java.util.concurrent.atomic.AtomicInteger;
public final class AtomicInventory {
    private final AtomicInteger available = new AtomicInteger(1);
    public boolean reserve(int units) {
        if (units <= 0) throw new IllegalArgumentException("Positive quantity required");
        int before = available.getAndUpdate(current -> current >= units ? current - units : current);
        return before >= units;
    }
}
