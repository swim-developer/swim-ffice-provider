# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Test Commands

```bash
# Full setup (clone deps + install to local Maven repo + pull)
make sync

# Build (compile + package JAR, skips test execution but compiles tests)
./mvnw clean package -DskipTests

# Unit tests only
./mvnw test

# Unit + integration tests (Testcontainers — requires Podman running)
./mvnw verify -DskipITs=false

# Run a single test class
./mvnw test -Dtest=EventExtractorTest

# Run a single integration test class
./mvnw verify -DskipITs=false -Dit.test=FficeProviderIT

# SonarQube analysis (start SonarQube first with: make sonar-up)
make sonar

# Local infrastructure (Artemis, PostgreSQL, Kafka, Keycloak)
podman compose up -d
```

**Important**: Integration tests are skipped by default (`<skipITs>true</skipITs>` in pom.xml). Always pass `-DskipITs=false` when running them, or they will be silently skipped. Never use `-Dmaven.test.skip=true` (it skips test compilation too — use `-DskipTests` instead). Use `podman`, never `docker`.

## Project Overview

Quarkus service implementing a SWIM FF-ICE (Flight and Flow Information for a Collaborative Environment) Provider. It exposes a Subscription Manager REST API and publishes FIXM/FF-ICE XML events to subscribers via AMQP (ActiveMQ Artemis).

**Event flow**: Kafka (ingress) -> XML parsing/validation (JAXB + XSD) -> PostgreSQL (persistence) -> AMQP Artemis (fan-out to subscriber queues)

### Sibling Dependencies (must be installed to local Maven repo before building)

- `swim-developer-root` (parent POM, install with `-N`)
- `swim-fixm-ffice-model` (JAXB-generated FIXM/FF-ICE types)
- `swim-developer-framework` (swim-framework-core, swim-framework-provider — base classes for providers)
- `swim-developer-extensions`

Use `make sync` to clone/pull and install all of them automatically.

## Architecture

Hexagonal Architecture (Ports & Adapters) based on *Designing Hexagonal Architecture with Java* by Davi Vieira.

```
domain/model/           — Domain entities (Subscription, StoredEvent, FilterableEvent, FailedDelivery)
application/port/in/    — Inbound ports (ManageSubscriptionPort, DeliverEventPort, QueryEventPort)
application/port/out/   — Outbound ports (SubscriptionStore, EventStore, MessageAssemblerPort, etc.)
application/usecase/    — Use cases (SubscriptionUseCase, EventDeliveryUseCase, EventQueryUseCase)
infrastructure/in/      — Inbound adapters
  amqp/                 — Kafka consumer (IngressMessageHandler — implements SwimIngressHandler from framework)
  rest/                 — JAX-RS resources (SubscriptionCollectionResource, SubscriptionItemResource, TopicResource, FeatureResource)
  internal/             — Internal HTTP server (health, management endpoints on separate port)
infrastructure/out/     — Outbound adapters
  persistence/          — JPA/Panache stores (ProviderEventStore, JpaSubscriptionStore)
  amqp/                 — AMQP publisher to Artemis subscriber queues
  xml/                  — JAXB unmarshalling pool, EventExtractor, MessageAssembler
  subscription/         — Subscription lifecycle adapters (heartbeat, expiry, hash, active-supplier)
  messaging/            — OutboxEventProcessor (Vert.x event bus for async delivery after TX commit)
```

Use cases extend abstract base classes from `swim-framework-provider` (e.g., `AbstractProviderSubscriptionService`, `AbstractEventDeliveryService`). The framework provides the template methods; this project fills in the FF-ICE-specific implementations.

### Key Patterns

- **Transactional Outbox**: Events are persisted in a DB transaction, then dispatched via Vert.x event bus after commit (`AfterCommitEventDispatcher` + `OutboxEventProcessor`).
- **JAXB pool**: `JaxbUnmarshallerPool` provides thread-safe XML unmarshalling with XSD validation for `FficeMessageType`.
- **Fault tolerance**: Ingress processing uses MicroProfile `@Retry`, `@Timeout`, `@CircuitBreaker`, `@Bulkhead`.
- **Subscription lifecycle**: TTL-based expiry with configurable default/max TTL, heartbeat, and hash-based deduplication.

## Coding Standards

- **Logging**: Always use `@Slf4j` (Lombok). Never `LoggerFactory.getLogger()`.
- **No inner classes**: Every class in its own file.
- **Max 400 lines per file** (except .md files).
- **No comments in code**.
- **No Java Reflection** — not in production, not in tests.
- **Tests**: Use RestAssured for HTTP requests, AssertJ for assertions. Integration tests use Testcontainers (PostgreSQL, Artemis, Kafka).
- **Never delete or disable code to make tests pass** — investigate and fix properly.
- **Never take design decisions autonomously** — always ask before executing.
