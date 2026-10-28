import java.nio.file.Path;
import java.util.List;

// Review fixture only: command construction is tested; no container is started.
public final class ScannerCommand {
    public static List<String> command(Path checkoutRoot, Path checkout) throws java.io.IOException {
        // checkoutRoot is trusted service configuration, never supplied by repository content.
        Path root = checkoutRoot.toRealPath();
        Path source = checkout.toRealPath();
        if (!java.nio.file.Files.isDirectory(root) || !java.nio.file.Files.isDirectory(source)
                || source.equals(root) || !source.startsWith(root)
                || source.toString().chars().anyMatch(c -> c == ',' || c == '"' || c == '\r' || c == '\n')) {
            throw new IllegalArgumentException("A checkout directory beneath the trusted root with a safe mount field is required");
        }
        return List.of("docker", "run", "--rm", "--network=none", "--read-only",
                "--cap-drop=ALL", "--security-opt=no-new-privileges",
                "--pids-limit=128", "--memory=512m", "--cpus=1", "--user=65532:65532",
                "--tmpfs=/tmp:rw,noexec,nosuid,size=64m",
                "--mount", "type=bind,src=" + source + ",dst=/src,readonly",
                "semgrep/semgrep:1.90.0", "semgrep", "scan", "--json", "/src");
    }
}
