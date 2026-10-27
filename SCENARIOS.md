# Synthetic review scenario

Inventory has a non-atomic check/decrement under concurrent requests. PaymentService can charge twice for one request ID because get/charge/put is not atomic. AtomicInventory is a single-process counterexample; distributed payment idempotency still needs a durable unique key and provider idempotency.

These files are isolated review fixtures, not a deployed application. Compare the vulnerable implementation with its named counterexample. The automated review must distinguish evidence-backed findings from incomplete coverage. No merge or deployment is requested.

Run the final structured-streaming reviewer and publish severity/status statistics, positive observations, and live UI results for this commit.
