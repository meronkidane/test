package example;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

/** Dependency-free regression checks; failures throw AssertionError. */
public final class OrderSummaryTest {
    public static void main(String[] args) {
        OrderSummary summary = new OrderSummary();
        check(summary.totalCents(List.of()) == 0);
        check(summary.totalCents(List.of(10, 20, 0)) == 30);
        check(summary.totalCents(List.of(Integer.MAX_VALUE, Integer.MAX_VALUE)) == 4294967294L);
        rejects(NullPointerException.class, () -> summary.totalCents(null));
        rejects(NullPointerException.class, () -> summary.totalCents(Arrays.asList(1, null)));
        rejects(IllegalArgumentException.class, () -> summary.totalCents(List.of(-1)));
        check(!summary.hasDuplicate(List.of()));
        check(!summary.hasDuplicate(List.of("a", "b")));
        check(summary.hasDuplicate(List.of("a", "b", "a")));
        rejects(NullPointerException.class, () -> summary.hasDuplicate(null));
        rejects(NullPointerException.class, () -> summary.hasDuplicate(Arrays.asList("a", "a", null)));
        rejects(IllegalArgumentException.class, () -> summary.hasDuplicate(List.of("a", " ")));
        check(!summary.hasDuplicate(IntStream.range(0, 10000).mapToObj(Integer::toString).toList()));
    }
    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Regression check failed");
    }
    private static void rejects(Class<? extends RuntimeException> expected, Runnable operation) {
        try { operation.run(); }
        catch (RuntimeException failure) {
            if (expected.isInstance(failure)) return;
            throw failure;
        }
        throw new AssertionError("Expected " + expected.getSimpleName());
    }
}
