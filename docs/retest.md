# Revalidation

Compile examples/messaging/PaymentConsumer.java and PaymentConsumerTest.java into a temporary directory and run PaymentConsumerTest with Java 21. Store durability is an explicit interface contract; this test simulates its boundary, not a production broker.

Run the strict review against the latest commit. An automated success still requires independent human approval. Do not suppress findings to obtain a passing result; investigate concrete defects and document false positives.
