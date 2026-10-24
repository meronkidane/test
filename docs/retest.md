# Revalidation

Compile examples/messaging/PaymentConsumer.java and PaymentConsumerTest.java into a temporary directory and run PaymentConsumerTest with Java 21. Store durability is an explicit interface contract; this test simulates its boundary, not a production broker.

Run the strict review against the latest commit. An automated success still requires independent human approval. Do not suppress findings to obtain a passing result; investigate concrete defects and document false positives.

## Finding confirmation

The automated reviewer now confirms model-generated candidates against surrounding
code before publishing them. Candidate and confirmed findings remain in its audit
record; unavailable or invalid confirmation fails closed. This fresh commit requests
revalidation of the existing tested implementation without weakening the strict policy.

Confirmation also evaluates each candidate explanation against the synchronous
store contract and tests: an exception must prevent acknowledgment, while a
successful durable apply must precede acknowledgment.
