public final class Inventory {
    private int available = 1;
    public boolean reserve(int units) {
        if (units <= 0) throw new IllegalArgumentException("Positive quantity required");
        if (available >= units) {
            available -= units;
            return true;
        }
        return false;
    }
}
