package example;

/** Deliberately unsafe scanner fixture. Do not execute or merge this example. */
public final class UnsafeCommandFixture {
    // Review integration fixture: expect a Major finding with remediation and references.
    public Process run(String userInput) throws java.io.IOException {
        return Runtime.getRuntime().exec(userInput);
    }
}
