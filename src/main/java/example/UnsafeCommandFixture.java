package example;

/** Deliberately unsafe scanner fixture. Do not execute or merge this example. */
public final class UnsafeCommandFixture {
    public Process run(String userInput) throws java.io.IOException {
        return Runtime.getRuntime().exec(userInput);
    }
}
