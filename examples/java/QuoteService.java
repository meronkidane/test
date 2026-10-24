import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

/** Exact USD quote calculation; invalid input is rejected with IllegalArgumentException. */
public final class QuoteService {
  private static final Pattern DECIMAL = Pattern.compile("[0-9]{1,7}(\\.[0-9]{1,6})?");
  private QuoteService() {}

  public static BigDecimal total(String unitPrice, int quantity) {
    if (quantity < 0 || quantity > 10000) throw new IllegalArgumentException("quantity");
    if (unitPrice == null || unitPrice.length() > 14 || !DECIMAL.matcher(unitPrice).matches())
      throw new IllegalArgumentException("price format");
    BigDecimal price = new BigDecimal(unitPrice);
    if (price.compareTo(new BigDecimal("1000000")) > 0)
      throw new IllegalArgumentException("price limit");
    // Round the exact extended price once, using the USD business rounding rule.
    return price.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_EVEN);
  }
}
