package example;

/** Dependency-free regression checks; run this main method with Java 21. */
public final class NamesTest {
    public static void main(String[] args) {
        check("Guest", Names.display(null));
        check("Guest", Names.display("  "));
        check("Ada", Names.display("  Ada  "));
        check("Grace", Names.display("Grace"));
    }
    private static void check(String expected, String actual) {
        if (!expected.equals(actual)) throw new AssertionError("Unexpected normalized name");
    }
}
