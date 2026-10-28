import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

// Review fixture only: command construction is tested; no process is started.
public final class ScannerCommand {
    public static List<String> command(Path checkout) throws java.io.IOException {
        Path source = checkout.toRealPath();
        if (!Files.isDirectory(source)) throw new IllegalArgumentException("Checkout directory required");
        // Pass directly to ProcessBuilder: never join these arguments into a shell command.
        return List.of("semgrep", "scan", "--json", "--", source.toString());
    }
}
