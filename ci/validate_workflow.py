"""Validate the review-only workflow fixture; requires PyYAML 6.x."""
from pathlib import Path
import yaml

path = Path(__file__).resolve().parents[1] / "examples/ci/review-workflow.yml"
workflow = yaml.load(path.read_text(), Loader=yaml.BaseLoader)
if workflow["on"] != "pull_request" or workflow["permissions"] != {"contents": "read"}:
    raise SystemExit("Workflow must use unprivileged pull_request execution")
job = workflow["jobs"]["validate"]
checkout = job["steps"][0]
if checkout["with"].get("persist-credentials") != "false":
    raise SystemExit("Checkout must not persist credentials")
revision = checkout["uses"].split("@", 1)[1]
if len(revision) != 40 or any(c not in "0123456789abcdef" for c in revision):
    raise SystemExit("Action must be pinned to a commit")
if int(job["timeout-minutes"]) > 10 or "secrets" in workflow:
    raise SystemExit("Unexpected workflow privilege or time budget")
print("PASS workflow trust boundary and immutable action reference")
