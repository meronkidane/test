package example;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Validates order data and calculates totals in integer cents. */
public final class OrderSummary {
    /** Rejects null or negative prices; an empty order totals zero cents. */
    public long totalCents(List<Integer> prices) {
        Objects.requireNonNull(prices, "prices");
        long total = 0;
        for (Integer price : prices) {
            Objects.requireNonNull(price, "price");
            if (price < 0) throw new IllegalArgumentException("Prices must be nonnegative");
            total = Math.addExact(total, price.longValue());
        }
        return total;
    }

    /** Validates every ID and detects duplicates in expected linear time. */
    public boolean hasDuplicate(List<String> ids) {
        Objects.requireNonNull(ids, "ids");
        Set<String> seen = new HashSet<>();
        boolean duplicate = false;
        for (String id : ids) {
            Objects.requireNonNull(id, "id");
            if (id.isBlank()) throw new IllegalArgumentException("IDs must not be blank");
            if (!seen.add(id)) duplicate = true;
        }
        return duplicate;
    }
}
