import java.nio.file.Path;
import java.util.List;

// Review fixture only: command construction is tested; no container is started.
public final class ScannerCommand {
    public static List<String> command(Path checkout) {
        if (!checkout.isAbsolute()) throw new IllegalArgumentException("Absolute path required");
        return List.of("docker", "run", "--rm", "--privileged",
                "--mount", "type=bind,src=" + checkout + ",dst=/src",
                "semgrep/semgrep:1.90.0", "semgrep", "scan", "--json", "/src");
    }
}
