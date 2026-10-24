# Revalidation

Run Terraform fmt -check, init -backend=false -lockfile=readonly and validate in infra. No plan or apply is required.

Run the strict review against the latest commit. An automated success still requires independent human approval. Do not suppress findings to obtain a passing result; investigate concrete defects and document false positives.

## Finding confirmation

The automated reviewer now confirms model-generated candidates against surrounding
code before publishing them. Candidate and confirmed findings remain in its audit
record; unavailable or invalid confirmation fails closed. This fresh commit requests
revalidation of the existing tested implementation without weakening the strict policy.
