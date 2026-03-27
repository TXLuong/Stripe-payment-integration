This project demonstrates a payment integration with Stripe using an asynchronous architecture powered by Kafka.
Instead of handling payment logic synchronously, the system publishes events to Kafka and processes them in the background, improving scalability, reliability, and decoupling.
