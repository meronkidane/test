package example;

/** Small runnable example used to validate automated pull request reviews. */
public final class Greeting {
    private Greeting() {}

    public static String message(String name) {
        String recipient = name == null ? "" : name.strip();
        return "Hello, " + (recipient.isEmpty() ? "world" : recipient) + "!";
    }

    public static void main(String[] args) {
        System.out.println(message(args.length == 0 ? null : args[0]));
    }
}
