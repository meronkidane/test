import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

// Verifies argument boundaries without launching a process.
public class ScannerCommandTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("shell-command-test");
        try {
            for (String name : List.of("normal", "space in path", "a;echo injected", "$(echo injected)",
                    "a'b\"c", "--config=attacker", "line\nbreak")) {
                Path checkout = Files.createDirectory(root.resolve(name));
                var command = ScannerCommand.command(checkout);
                if (!command.equals(List.of("semgrep", "scan", "--json", "--", checkout.toRealPath().toString())))
                    throw new AssertionError("Checkout must remain one argument after the option terminator");
            }
            try {
                ScannerCommand.command(root.resolve("missing"));
                throw new AssertionError("Missing path accepted");
            } catch (java.io.IOException expected) { }
            Path file = Files.createFile(root.resolve("file"));
            try {
                ScannerCommand.command(file);
                throw new AssertionError("File accepted as checkout");
            } catch (IllegalArgumentException expected) { }
            System.out.println("Shell injection and option-boundary regression checks passed");
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }
}
