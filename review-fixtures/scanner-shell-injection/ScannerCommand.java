import java.nio.file.Path;
import java.util.List;

// Review fixture only: command construction is tested; no process is started.
public final class ScannerCommand {
    public static List<String> command(Path checkout) {
        return List.of("sh", "-c", "semgrep scan --json " + checkout);
    }
}
