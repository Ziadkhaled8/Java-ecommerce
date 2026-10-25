# Dream Store — Enterprise E-Commerce Platform

A production-grade, highly resilient e-commerce backend built with **Java 24**, **Spring Boot 4**, and modern distributed systems design patterns.

---

## Architecture Overview

```mermaid
flowchart TD
    Client([Client / Frontend]) -->|HTTP REST + Correlation-ID| API[Spring Boot API Gateway / Controller]
    
    subgraph Core Domain & Concurrency
        API --> ProductSvc[Product Service\n@Version Optimistic Locking]
        API --> OrderSvc[Order Service\nIdempotency Key Check]
        API --> PaymentSvc[Payment Service]
    end

    subgraph Resilient Payment Subsystem
        PaymentSvc --> StrategyReg[Payment Strategy Registry]
        StrategyReg --> StripeStrat[Stripe Payment Strategy]
        StripeStrat --> CircuitBreaker[Payment Circuit Breaker\nCLOSED | OPEN | HALF_OPEN]
        CircuitBreaker -->|Stripe Checkout API| Stripe[(Stripe Gateway)]
        Stripe -.->|Webhook: checkout.session.completed| WebhookHandler[Webhook Handler]
        WebhookHandler --> StateMachine[Payment State Machine]
    end

    subgraph Transactional Outbox Pattern
        StateMachine -->|1. Atomic DB Commit| DB[(MySQL: Order + Payment + OutboxMessage)]
        DB -->|2. Fast-Path: AFTER_COMMIT Async| OutboxListener[Outbox Event Listener]
        DB -.->|3. Safety Net: Every 30s| Poller[Outbox Poller Sweeper]
        OutboxListener -->|Publish| RabbitMQ[[RabbitMQ: order.events.exchange]]
        Poller -->|Publish on Failure| RabbitMQ
    end

    subgraph Decoupled Notification Microservice
        RabbitMQ -->|Queue: order.invoice.email.queue| Consumer[Invoice Email Consumer]
        Consumer --> EmailSvc[Email Service]
        EmailSvc --> SMTP[(SMTP Server: MailHog / Mailpit / Production)]
        SMTP --> CustomerInbox([Customer Inbox: Plain Text Invoice])
    end
```

---

## Design Patterns & Architectural Decisions

### 1. Strategy Pattern (Payment Provider Abstraction)
* **Location**: `com.ecommerce.dreamstore.payment.strategy`
* **Implementation**: `PaymentStrategy` interface with concrete `StripePaymentStrategy` implementation and a dynamic `PaymentStrategyRegistry`.
* **Why Chosen**: Allows the platform to support multiple payment providers (Stripe, PayPal, Mock) without modifying order processing or checkout orchestrators.
* **Architectural Effect**:
  * Satisfies the **Open-Closed Principle (OCP)** — adding a new payment gateway only requires introducing a new bean implementing `PaymentStrategy`.
  * Isolates vendor-specific SDK models (e.g. Stripe `SessionCreateParams`) from core business logic.

---

### 2. Circuit Breaker Pattern (External Gateway Resilience)
* **Location**: `com.ecommerce.dreamstore.payment.circuitbreaker`
* **Implementation**: Thread-safe, Spring-managed `PaymentCircuitBreaker` using atomic primitives (`AtomicReference`, `AtomicInteger`, `AtomicLong`).
* **States**:
  * **`CLOSED`**: Requests proceed normally. Consecutive failures increment a counter. When reaching the failure threshold (default `5`), it trips to `OPEN`.
  * **`OPEN`**: Fails immediately with `CircuitBreakerOpenException` (`503 Service Unavailable` with `retryAfterSeconds`), protecting system threads from blocking during external downtime.
  * **`HALF_OPEN`**: After a reset timeout (default `30s`), allows trial probes. If consecutive probes succeed, it recovers to `CLOSED`; if any fail, it reverts to `OPEN`.
* **Architectural Effect**:
  * Prevents **cascading system failure** and thread pool exhaustion when Stripe undergoes network degradation or downtime.
  * Provides graceful client feedback via standard RFC 7807 `ProblemDetail` responses.

---

### 3. State Machine Pattern (Payment Lifecycle Governance)
* **Location**: `com.ecommerce.dreamstore.payment.statemachine`
* **Implementation**: `PaymentStateMachine` enforcing an immutable transition table over states: `PENDING`, `AUTHORIZED`, `COMPLETED`, `FAILED`, `CANCELLED`, `REFUNDED`.
* **Transitions**:
  * `PENDING` $\xrightarrow{\text{COMPLETE}}$ `COMPLETED`
  * `PENDING` $\xrightarrow{\text{CANCEL}}$ `CANCELLED`
  * `PENDING` $\xrightarrow{\text{FAIL}}$ `FAILED`
  * `COMPLETED` $\xrightarrow{\text{REFUND}}$ `REFUNDED`
* **Architectural Effect**:
  * **State Corruption Prevention**: Rejects invalid transitions (e.g., refunding an unpaid order or failing a completed order) by throwing `InvalidPaymentStateTransitionException`.
  * **Side-Effect Encapsulation**: Automatically synchronizes the associated `Order` entity (setting `isPaid = true` and transitioning status from `PENDING` $\rightarrow$ `PROCESSING`).

---

### 4. Transactional Outbox Pattern (Dual-Dispatch & Reliable Messaging)
* **Location**: `com.ecommerce.dreamstore.outbox`
* **Implementation**:
  * An `outbox_messages` table indexed on `(status, created_at)`.
  * Event persistence runs inside the caller's active database transaction (`Propagation.MANDATORY`).
  * **Dual-Dispatch Hybrid Architecture**:
    1. **Immediate Fast-Path**: Spring's `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` dispatches the message to RabbitMQ asynchronously within milliseconds of DB commit.
    2. **Background Safety Net**: A scheduled `OutboxPoller` sweeps every 30 seconds for any `PENDING` messages that failed immediate dispatch (e.g., due to a temporary broker blip).
* **Architectural Effect**:
  * **Zero Dual-Write Inconsistency**: Guarantees database state and message bus events remain 100% consistent without requiring distributed two-phase commits (2PC).
  * **Sub-10ms Latency**: Customers receive invoice emails almost instantaneously, without waiting for batch polling intervals.

---

### 5. Optimistic Locking & Concurrency Control
* **Location**: `Product.java`, `OrderServiceImpl.java`
* **Implementation**: Hibernate `@Version private Long version;` on `Product`. `OrderServiceImpl` runs order stock deduction inside an isolated `TransactionTemplate` with automatic exponential backoff retry on `OptimisticLockingFailureException`.
* **Architectural Effect**:
  * Eliminates database deadlocks caused by traditional pessimistic database locks (`SELECT ... FOR UPDATE`).
  * Prevents **inventory overselling** under high concurrent traffic while maximizing database read/write throughput.

---

### 6. Idempotency Key Enforcement
* **Location**: `OrderController.java`, `OrderServiceImpl.java`, `PaymentController.java`, `PaymentServiceImpl.java`
* **Implementation**:
  * Scoped unique constraints on database tables: `uk_order_idempotency` (`idempotency_key`, `user_id`) and `uk_payment_idempotency` (`idempotency_key`, `user_id`).
  * Accepts `Idempotency-Key` via standard HTTP request headers or request body.
  * Outbound Stripe API requests forward the key via `RequestOptions.builder().setIdempotencyKey(...)`.
  * Webhook listener checks payment status before processing to ensure repeated webhook deliveries are safely ignored.
* **Architectural Effect**:
  * Completely prevents double billing and duplicate order placement caused by network timeouts, client retries, or duplicate webhook notifications.

---

### 7. Observability & Distributed Tracing
* **Location**: `CorrelationIdFilter.java`, `RequestLoggingFilter.java`, `GlobalExceptionHandler.java`
* **Implementation**:
  * Extracts or generates `X-Correlation-Id` on incoming requests.
  * Injects correlation ID into SLF4J `MDC` context for unified structured logging (`[%X{correlationId}]`).
  * Propagates correlation ID in all outbound response headers and RFC 7807 `ProblemDetail` error bodies.
* **Architectural Effect**:
  * Enables fast end-to-end incident investigation and distributed request tracing across services.

---

## Decoupled Notification Architecture

The notification consumer (`com.ecommerce.dreamstore.notification`) is designed to be **completely independent** from the core system:
* It consumes `InvoiceNotificationEvent` — a pure data transfer record containing all customer, order, and line item details.
* **Zero JPA Dependencies**: Contains no references to `Order`, `Payment`, `Product`, or database repositories.
* Can be extracted into a separate, standalone microservice container at any time with zero refactoring.
* Formats and dispatches itemized plain-text invoice receipts via SMTP (`JavaMailSender`).

---

## Tech Stack

| Layer | Technology |
| :--- | :--- |
| **Language & Platform** | Java 24, Spring Boot 4.1.1 |
| **Persistence & DB** | Spring Data JPA, Hibernate, MySQL |
| **Security** | Spring Security 6, JJWT (HMAC-SHA256) |
| **Message Broker** | RabbitMQ (Topic Exchange, Dead Letter Exchange & Queue) |
| **Payment Gateway** | Stripe Java SDK 34.0.0 (Hosted Checkout Sessions) |
| **Email Service** | Spring Boot Mail Starter (JavaMailSender / SMTP) |
| **API Documentation** | SpringDoc OpenAPI 3 / Swagger UI |
| **Build Tool** | Apache Maven (Maven Wrapper included) |

---

## Configuration & Environment Variables

Key configuration properties in `src/main/resources/application.properties`:

| Property | Environment Variable | Default Value | Description |
| :--- | :--- | :--- | :--- |
| `stripe.api-key` | `STRIPE_API_KEY` | `sk_test_placeholder` | Stripe Secret API Key |
| `stripe.webhook-secret` | `STRIPE_WEBHOOK_SECRET` | `whsec_placeholder` | Stripe Webhook Signing Secret |
| `spring.rabbitmq.host` | `RABBITMQ_HOST` | `localhost` | RabbitMQ Host |
| `spring.rabbitmq.port` | `RABBITMQ_PORT` | `5672` | RabbitMQ Port |
| `spring.mail.host` | `SMTP_HOST` | `localhost` | SMTP Server Host |
| `spring.mail.port` | `SMTP_PORT` | `1025` | SMTP Server Port (e.g. MailHog / Mailpit) |
| `payment.circuit-breaker.failure-threshold` | — | `5` | Failures before opening circuit |
| `payment.circuit-breaker.reset-timeout-ms` | — | `30000` | Cooldown in ms before half-open probe |
| `outbox.poller.fixed-delay-ms` | — | `30000` | Fallback outbox sweeper interval |

> **Note**: For local development secrets, create `src/main/resources/application-local.properties` (automatically ignored by Git).

---

## Getting Started

### 1. Prerequisites
* **Java 24** installed
* **MySQL** running on port 3306 (`dream_shops_db`)
* **RabbitMQ** running on port 5672
* **MailHog** or **Mailpit** running on port 1025 (for viewing sent emails)

### 2. Build & Run
```bash
# Clone the repository
git clone <repo-url>
cd dream-store

# Compile and package
./mvnw clean install

# Run the application
./mvnw spring-boot:run
```

### 3. API Documentation
Once started, explore the interactive Swagger documentation at:
```
http://localhost:9193/swagger-ui/index.html
```
