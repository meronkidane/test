# Revalidation

With PyYAML 6.x installed, run sh ci/test.sh. The workflow remains outside .github/workflows and must not execute as a privileged deployment workflow.

Run the strict review against the latest commit. An automated success still requires independent human approval. Do not suppress findings to obtain a passing result; investigate concrete defects and document false positives.
