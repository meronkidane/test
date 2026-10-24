# Local review checklist

1. Open a pull request against the default branch.
2. Confirm that the GitHub App receives the pull request event.
3. Wait for the enterprise-review check to finish on the latest commit SHA.
4. Read its summary and any inline findings on that reviewed commit.
5. Resolve findings, run relevant regression tests, and push a new commit.
6. Verify the new review completed successfully, then request human approval.

## When analysis is incomplete

An incomplete automated review is not a passing result, even when its finding
count is zero. Resolve unavailable dependencies or provider quota problems and
request a new review. Do not disable required checks to obtain a green result.

## Reading feedback

Review comments show completed analysis steps and findings with Low, Medium, Major,
and Critical severity labels. Follow the suggested fix and verification steps,
and consult linked references in the context of the actual code change.

Source-location validation does not prove runtime impact or absence of vulnerabilities.
All validated severities block under the strict policy; independent human approval remains required.

This document introduces no executable code or infrastructure resources.
