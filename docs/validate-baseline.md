# Validate the clean baseline

Requires Java 21 and Terraform 1.6 or newer (below 2.0).

Run the Java checks from the repository root:

```sh
build_dir=$(mktemp -d)
javac --release 21 -d "$build_dir" src/main/java/example/Names.java src/test/java/example/NamesTest.java
java -cp "$build_dir" example.NamesTest
```

The check covers null, blank, padded, and already-normalized names.
Delete the temporary build directory after inspection.

Validate the provider-free Terraform example without provisioning resources:

```sh
terraform -chdir=infra init -backend=false
terraform -chdir=infra validate
```

A successful local test does not replace the GitHub review check. AI provider
unavailability must remain visible as an incomplete review.
