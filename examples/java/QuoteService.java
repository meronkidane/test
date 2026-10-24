import java.math.BigDecimal;
import java.math.RoundingMode;
// Application service called by the authenticated quote endpoint.
// Contract: return exact USD amounts with two decimal places and HALF_EVEN rounding.
public final class QuoteService {
  public static BigDecimal total(String unitPrice, int quantity) {
    if (quantity < 0 || quantity > 10000) throw new IllegalArgumentException("quantity");
    BigDecimal price = new BigDecimal(unitPrice);
    if (price.signum() < 0 || price.compareTo(new BigDecimal("1000000")) > 0)
      throw new IllegalArgumentException("price");
    return new BigDecimal(price.doubleValue() * quantity).setScale(2, RoundingMode.HALF_EVEN);
  }
}
