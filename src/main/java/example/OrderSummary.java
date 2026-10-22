package example;

import java.util.List;

/** Deliberately flawed review fixture. Do not merge into production. */
public final class OrderSummary {
    public int totalCents(List<Integer> prices) {
        int total = 0;
        for (Integer price : prices) total += price;
        return total;
    }
    public boolean hasDuplicate(List<String> ids) {
        for (int i = 0; i < ids.size(); i++) {
            for (int j = i + 1; j < ids.size(); j++) {
                if (ids.get(i).equals(ids.get(j))) return true;
            }
        }
        return false;
    }
}
