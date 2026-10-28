# Shell-free scanner command regression fixture

The original vulnerable `sh -c` command is replaced by a direct argument list.
The canonical checkout directory occupies one argument after `--`, so whitespace,
shell metacharacters and option-looking names cannot become shell instructions
or scanner flags. Callers must pass the list directly to ProcessBuilder, never
join it into a shell string. This helper is not a process sandbox; actual scanning
of untrusted repositories belongs in an isolated runner.

Run with Java 21 (no scanner or shell process is launched by the test):

```sh
classes=$(mktemp -d)
javac -d "$classes" ScannerCommand.java ScannerCommandTest.java
java -cp "$classes" ScannerCommandTest
rm -r "$classes"
```
