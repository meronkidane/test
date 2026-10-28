# Hardened scanner command fixture

`command(checkoutRoot, checkout)` builds arguments only. The service supplies a
trusted checkout root; repository content must not choose it. Both paths resolve
symlinks before requiring the checkout to be a strict descendant of that root.
Docker mount CSV delimiters/quotes and line breaks are rejected. Arguments are
passed directly; shell escaping must not be added to a ProcessBuilder argument.

The caller must own and protect the checkout directory from concurrent replacement
between validation and container startup. This helper alone cannot eliminate that
filesystem race. Repository code must not run on the host to prepare the checkout.

Run the regression checks with Java 21 (no containers or subprocesses are launched):

```sh
classes=$(mktemp -d)
javac -d "$classes" ScannerCommand.java ScannerCommandTest.java
java -cp "$classes" ScannerCommandTest
rm -r "$classes"
```
