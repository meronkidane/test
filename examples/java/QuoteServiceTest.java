import java.math.BigDecimal;

public final class QuoteServiceTest {
  public static void main(String[] args) {
    check("1.02", "1.015", 1);
    check("1.00", "1.005", 1);
    check("0.30", "0.1", 3);
    check("0.00", "1", 0);
    check("10000000000.00", "1000000", 10000);
    for (String invalid : new String[] {null, "", "-1", "NaN", "1e400", "1,20", "1000001", "9".repeat(10000)}) {
      try { QuoteService.total(invalid, 1); throw new AssertionError("invalid price accepted"); }
      catch (IllegalArgumentException expected) { }
    }
    for (int invalid : new int[] {-1, 10001}) {
      try { QuoteService.total("1", invalid); throw new AssertionError("invalid quantity accepted"); }
      catch (IllegalArgumentException expected) { }
    }
    System.out.println("PASS monetary precision, rounding, bounds and invalid input");
  }
  private static void check(String expected, String price, int quantity) {
    if (!new BigDecimal(expected).equals(QuoteService.total(price, quantity)))
      throw new AssertionError("incorrect total");
  }
}
