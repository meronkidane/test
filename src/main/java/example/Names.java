package example;

/** Normalizes optional display names without external side effects. */
public final class Names {
    private Names() {}
    public static String display(String value) {
        return value == null || value.isBlank() ? "Guest" : value.strip();
    }
}
