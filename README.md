# Code review integration test

This repository exercises the local GitHub App code review integration.

## Run the Java example

Requires Java 21. From the repository root:

```sh
javac -d /tmp/review-greeting src/main/java/example/Greeting.java
java -cp /tmp/review-greeting example.Greeting
java -cp /tmp/review-greeting example.Greeting "  Ada  "
```

Expected output is `Hello, world!` and `Hello, Ada!`, respectively.
A blank name also uses `world`.

Opening or updating a pull request should trigger the installed review App.
Review results appear in the `enterprise-review` check on the pull request.
