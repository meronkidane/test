import java.nio.file.Path;
import java.util.List;

// Review fixture only: command construction is tested; no container is started.
public final class ScannerCommand {
    public static List<String> command(Path checkout) throws java.io.IOException {
        Path source = checkout.toRealPath();
        if (!java.nio.file.Files.isDirectory(source) || source.toString().contains(",")) {
            throw new IllegalArgumentException("A canonical directory without commas is required");
        }
        return List.of("docker", "run", "--rm", "--network=none", "--read-only",
                "--cap-drop=ALL", "--security-opt=no-new-privileges",
                "--pids-limit=128", "--memory=512m", "--cpus=1", "--user=65532:65532",
                "--tmpfs=/tmp:rw,noexec,nosuid,size=64m",
                "--mount", "type=bind,src=" + source + ",dst=/src,readonly",
                "semgrep/semgrep:1.90.0", "semgrep", "scan", "--json", "/src");
    }
}
