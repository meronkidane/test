# Automatic webhook review scenarios

Synthetic fixtures for a real GitHub PR opened event -> signed webhook -> transactional outbox -> SNS/SQS -> worker -> MCP -> persisted review.

These snippets are static test data, not deployable application code. Do not run them. The workflows are manual-only and their sole job is disabled.

| Scenario | Positive fixture | Negative counterexample |
|---|---|---|
| Java authorization | permitAll | authenticated |
| TypeScript HTML trust | bypassSecurityTrustHtml | plain value |
| Terraform IAM | wildcard action | s3:GetObject |
| SQL migration | DROP TABLE | CREATE TABLE |
| PySpark | repartition(1) | repartition(32) |
| CI permissions | write-all | read-all |

`scenarios/expectations.json` identifies the six positive and six negative files. `scenarios/fake-secret.txt` contains an obviously fake demonstration marker.

Expected: six baseline findings at exact source locations, no baseline findings in negative files, secret marker detected, policy gate BLOCK, immutable head SHA and MCP evidence recorded. Model output is evidence-validated separately; absent semantic scanners remain INCOMPLETE. A completed review is not a clean gate.

The PR is intended to test automatic GitHub delivery, without a local webhook replay. No deployment, merge, or code execution is required.

This revision requests a fresh review after the reactive model, async MCP, OIDC, and GitHub publication refactor.

Run the final structured-streaming reviewer and publish severity/status statistics, positive observations, and live UI results for this commit.
