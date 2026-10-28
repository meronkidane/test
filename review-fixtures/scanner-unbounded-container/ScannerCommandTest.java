import java.nio.file.Files;
import java.nio.file.Path;

// Builds argument lists only. Never launches Docker or repository code.
public class ScannerCommandTest {
    interface Attempt { void run() throws Exception; }
    static void denied(Attempt attempt) throws Exception {
        try { attempt.run(); } catch (IllegalArgumentException | java.io.IOException expected) { return; }
        throw new AssertionError("Unsafe checkout accepted");
    }
    public static void main(String[] args) throws Exception {
        Path temporary = Files.createTempDirectory("scanner-command-test");
        try {
            Path root = Files.createDirectory(temporary.resolve("checkouts"));
            Path good = Files.createDirectory(root.resolve("repo with spaces;$(echo nope)"));
            var command = ScannerCommand.command(root, good);
            if (!command.contains("type=bind,src=" + good.toRealPath() + ",dst=/src,readonly")
                    || !command.contains("--network=none") || !command.contains("--cap-drop=ALL")
                    || !command.contains("--read-only") || command.contains("sh")
                    || command.contains("--privileged") || !command.contains("--security-opt=no-new-privileges")
                    || !command.contains("--pids-limit=128") || !command.contains("--memory=512m")
                    || !command.contains("--cpus=1") || !command.contains("--user=65532:65532")
                    || !command.contains("--tmpfs=/tmp:rw,noexec,nosuid,size=64m"))
                throw new AssertionError("Expected isolated direct-argument command");
            Path outside = Files.createDirectory(temporary.resolve("outside"));
            Path prefix = Files.createDirectory(temporary.resolve("checkouts-other"));
            Path link = Files.createSymbolicLink(root.resolve("escape"), outside);
            denied(() -> ScannerCommand.command(root, root));
            denied(() -> ScannerCommand.command(root, outside));
            denied(() -> ScannerCommand.command(root, prefix));
            denied(() -> ScannerCommand.command(root, link));
            denied(() -> ScannerCommand.command(root, root.resolve("../outside")));
            denied(() -> ScannerCommand.command(root, root.resolve("missing")));
            Path file = Files.createFile(root.resolve("file"));
            denied(() -> ScannerCommand.command(root, file));
            for (String name : new String[]{"bad,path", "bad\"path", "bad\npath", "bad\rpath"}) {
                Path bad = Files.createDirectory(root.resolve(name));
                denied(() -> ScannerCommand.command(root, bad));
            }
            System.out.println("Scanner path and isolation regression checks passed");
        } finally {
            try (var paths = Files.walk(temporary)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }
}
