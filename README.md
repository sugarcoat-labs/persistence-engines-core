# persistence-engines-core

Provides thread-safe, in-memory persistence engines. It is a lightweight,
high-performance, zero-dependency pure Java core module. It is part of the
**sugarcoat-labs** ecosystem and is designed for clean integration under
Hexagonal Architecture (Ports & Adapters) model, serving as a reusable,
realistic, deterministic alternative to brittle mocks in unit tests and
a fast persistence core for integration testing.

---

## 📑 Table of Contents

- [📖 Public API Reference](#-public-api-reference)
    - [1. `EventStreamEngine<S, E_ID, E>`](#1-eventstreamengines-e_id-e)
    - [2. `StateStoreEngine<ID, T>`](#2-statestoreengineid-t)
- [🌍 Declaration of Compatibility & Production Database Analogs](#-declaration-of-compatibility--production-database-analogs)
    - [1. `EventStreamEngine` Compatibility (Append-Only, OCC & Global Idempotency)](#1-eventstreamengine-compatibility-append-only-occ--global-idempotency)
    - [2. `StateStoreEngine` Compatibility (Mutable Latest-State Store)](#2-statestoreengine-compatibility-mutable-latest-state-store)
- [🎯 Core Philosophy](#-core-philosophy)
- [🧠 Behavioral Model: Event History vs. Current State](#-behavioral-model-event-history-vs-current-state)
    - [Which Engine Should I Choose?](#which-engine-should-i-choose)
- [🧩 Type Agnosticism & Domain Freedom](#-type-agnosticism--domain-freedom)
- [🚀 User's Guide](#-users-guide)
    - [1. Partitioned Event Stream Engine (`EventStreamEngine`)](#1-partitioned-event-stream-engine-eventstreamengine)
    - [2. Mutable State Store Engine (`StateStoreEngine`)](#2-mutable-state-store-engine-statestoreengine)
- [🏛 Hexagonal Architecture Integration](#-hexagonal-architecture-integration)
- [📦 Package Architecture & JPMS Visibility](#-package-architecture--jpms-visibility)
- [🔌 Build & Dependency Configuration](#-build--dependency-configuration)
    - [Local Build, Test & Javadoc Verification](#local-build-test--javadoc-verification)
    - [Client Dependency Declaration](#client-dependency-declaration)
- [🧪 Technical Specifications & Concurrency Guarantees](#-technical-specifications--concurrency-guarantees)
- [📄 License](#-license)

---

## 📖 Public API Reference

### 1. `EventStreamEngine<S, E_ID, E>`
*Package: `com.sugarcoatlabs.persistence.event`*

```java
public interface EventStreamEngine<S, E_ID, E> {
    
    /** Appends an event if sequence == currentSequence + 1 and eventId is unseen. */
    void append(S streamId, E_ID eventId, long eventSequence, long expectedSequence, E event);

    /** Returns the highest persisted sequence number for streamId, or 0L if empty. */
    long currentSequence(S streamId);

    /** Returns an unmodifiable chronological snapshot of all events in streamId. */
    List<E> stream(S streamId);
}
```

- **`append(...)`** atomically validates that both `eventSequence` and
  `expectedSequence` equal `currentSequence(streamId) + 1` (throwing
  `OptimisticConcurrencyException` otherwise) and that `eventId` has never been
  persisted (throwing `DuplicateEventException` otherwise) before appending the
  event to the stream.
- **`currentSequence(streamId)`** performs a lock-free read of the stream's
  latest 1-indexed sequence number, returning `0L` if the stream has no events.
- **`stream(streamId)`** returns an unmodifiable chronological snapshot
  (`List.of()` if empty) safe for concurrent iteration without locking.

### 2. `StateStoreEngine<ID, T>`
*Package: `com.sugarcoatlabs.persistence.state`*

```java
public interface StateStoreEngine<ID, T> {
    
    /** Stores or overwrites the state snapshot associated with id. */
    void save(ID id, T entity);

    /** Retrieves the state snapshot for id, or Optional.empty() if absent. */
    Optional<T> findById(ID id);

    /** Returns an unmodifiable snapshot of all currently stored entities. */
    List<T> findAll();

    /** Returns true if an entry exists for id. */
    boolean exists(ID id);

    /** Removes the entry for id and returns true if one was present. */
    boolean deleteById(ID id);
}
```

- **`save(id, entity)`** performs an atomic, idempotent upsert that stores a new
  entry or replaces any existing snapshot for `id`.
- **`findById(id)`** retrieves the current snapshot wrapped in an `Optional`,
  never returning `null`.
- **`findAll()`** returns an unmodifiable point-in-time list of all stored
  entities (`List.of()` when empty).
- **`exists(id)`** checks key presence in $O(1)$ time without allocating or
  copying snapshots.
- **`deleteById(id)`** atomically removes the entry for `id` and reports
  whether an entry was actually deleted.


## 🌍 Declaration of Compatibility & Production Database Analogs

The behavioral contracts of `EventStreamEngine` and `StateStoreEngine` are
modeled on proven concurrency and consistency primitives in mainstream
production databases. Because the two engines enforce fundamentally different
invariants, each maps to its own set of database primitives and caveats:

### 1. `EventStreamEngine` Compatibility (Append-Only, OCC & Global Idempotency)

| Target Engine | Native Implementation Strategy | Specification Caveats |
| :--- | :--- | :--- |
| **PostgreSQL** *(Relational)* | Table with `UNIQUE (stream_id, sequence_number)` + `UNIQUE (event_id)`. Inspect SQLSTATE `23505` constraint name to distinguish `OptimisticConcurrencyException` vs. `DuplicateEventException`. | None. Full ACID parity out of the box. |
| **MySQL 8.0+** *(InnoDB)* | Table with `UNIQUE (stream_id, sequence_number)` + `UNIQUE (event_id)`. Parse Error `1062` (SQLSTATE `23000`) index name from the exception message. | Requires **InnoDB** storage engine for ACID row locks. |
| **Amazon DynamoDB** *(Key-Value)* | `TransactWriteItems` combining a stream item `Put` (`attribute_not_exists(sequence)`) and an idempotency sentinel `Put` (`attribute_not_exists(eventId)`). Inspect `CancellationReasons`. | `stream()` requires `ConsistentRead=true` on `Query` to guarantee read-your-writes consistency. |
| **MongoDB** *(Document)* | Event-per-document collection with two unique indexes (`{streamId: 1, sequence: 1}` and `{eventId: 1}`), inspecting `DuplicateKey` (`E11000`) index names. | Configure `WriteConcern.MAJORITY` and `ReadConcern.MAJORITY` for linearizable reads. |
| **KurrentDB** *(EventStoreDB)* | Native `appendToStream` with `ExpectedRevision` (`WrongExpectedVersionException` &rarr; `OptimisticConcurrencyException`). | **Behavioral delta:** KurrentDB scopes `EventId` idempotency per stream (not globally) and silently succeeds on duplicates instead of throwing `DuplicateEventException`. |

- **1-Indexed Sequence Alignment:** `EventStreamEngine` uses 1-indexed
  sequence numbers (`0L` = empty stream). Adapters targeting 0-indexed event
  stores (such as KurrentDB revisions) must translate `sequence - 1` at the
  adapter boundary.
- **Unbounded History vs. Pagination:** `EventStreamEngine#stream(S)` returns
  the complete chronological history for a stream partition, matching
  aggregate-sized streams. For multi-million-event streams in production, add a
  dedicated paginated query port in your application layer.

### 2. `StateStoreEngine` Compatibility (Mutable Latest-State Store)

| Target Engine | Native Implementation Strategy | Specification Caveats |
| :--- | :--- | :--- |
| **PostgreSQL** *(Relational)* | `INSERT ... ON CONFLICT (id) DO UPDATE` for `save()`; `DELETE ... RETURNING 1` for `deleteById()`. | None. Full ACID parity out of the box. |
| **MySQL 8.0+** *(InnoDB)* | `INSERT ... ON DUPLICATE KEY UPDATE` for `save()`; `DELETE FROM ... WHERE id = ?` (`affectedRows > 0`) for `deleteById()`. | Requires **InnoDB**. Note that `ON DUPLICATE KEY UPDATE` returns `2` affected rows on update (`0` if unchanged). |
| **Amazon DynamoDB** *(Key-Value)* | `PutItem` for `save()`; `DeleteItem` (`ReturnValues: ALL_OLD`) for `deleteById()`. | Use `ConsistentRead=true` on `GetItem` if strict read-your-writes consistency is required; `findAll()` (`Scan`) is eventually consistent. |
| **MongoDB** *(Document)* | `replaceOne({ _id: id }, doc, { upsert: true })` for `save()`; `deleteOne({ _id: id })` for `deleteById()`. | Configure `WriteConcern.MAJORITY` and `ReadConcern.MAJORITY` for linearizable reads. |
| **KurrentDB** *(EventStoreDB)* | Snapshot stream per entity (`MaxCount = 1`) or dedicated read-model projection store. | Not a native key-value store; prefer a relational or document store for `StateStoreEngine` adapters. |

- **Last-Write-Wins Semantics:** `StateStoreEngine` enforces idempotent
  overwrites without version checks. If your entity requires optimistic
  versioning on updates, model it as an event-sourced aggregate on
  `EventStreamEngine` instead.

---

## 🎯 Core Philosophy

This module is built strictly for **Java 24+** (with full JPMS `module-info.java`
encapsulation) and deliberately excludes external frameworks (Spring, Micronaut,
Quarkus), database drivers, and test libraries from its production bytecode.

It serves as a **zero-dependency, pure-JDK persistence core** designed around
strict architectural boundaries:
- **Facade + Interface Segregation (ISP):** A single sealed entry point
  (`PersistenceEnginesJdk`) provides direct access to completely independent,
  narrowly scoped engines (`EventStreamEngine`, `StateStoreEngine`). Consumers
  only depend on the exact behavioral contract they need.
- **Encapsulated Test Lifecycle Hooks:** Destructive reset operations
  (`ResettableEngine#clear()`) live in the unexported
  `com.sugarcoatlabs.persistence.lifecycle` package rather than polluting
  `EventStreamEngine` or `StateStoreEngine`. Application code never sees or
  invokes `.clear()`.
- **Loose Coupling:** The engines know nothing about your domain models,
  business exceptions, or application ports. You integrate them via thin
  **Object Adapters** inside your application that translate between your
  domain contracts and these generic engines.
- **Production-Grade Behavioral Parity:** Enforces strict optimistic
  concurrency, idempotency, and immutability rules out of the box—matching the
  consistency guarantees of real databases without booting external
  infrastructure during fast unit testing or local prototyping.

---

## 🧠 Behavioral Model: Event History vs. Current State

While both engines organize data by key under the hood, they enforce
fundamentally different **data lifecycle semantics**:

| Engine | Behavioral Model | Write Semantics | Concurrency Guarantees |
| :--- | :--- | :--- | :--- |
| **`EventStreamEngine`**<br>*(Append-Only Log)* | **Immutable History**<br>(`StreamId → List<Event>`) | Strictly **append-only**. Past facts are never overwritten or deleted. | Per-stream `ReentrantLock` partitions, global `ReentrantReadWriteLock` barrier, monotonic sequence checks, and `eventId` deduplication. |
| **`StateStoreEngine`**<br>*(Entity / Registry Store)* | **Mutable Latest State**<br>(`EntityId → Entity`) | **Idempotent upsert**. Saving an existing key replaces its previous snapshot. | Lock-free atomic updates via `ConcurrentHashMap` with unmodifiable collection read boundaries. |

### Which Engine Should I Choose?

| Use Case | Recommended Engine | Why |
| :--- | :--- | :--- |
| **Event-Sourced aggregates & domain workflows** *(e.g., Order Placed → Paid → Shipped)* | **`EventStreamEngine`** | Current state is derived by replaying ordered facts. Optimistic locking prevents concurrent commands from corrupting aggregate state. |
| **Financial ledgers, billing & audit trails** *(e.g., wallet credits/debits, compliance logs)* | **`EventStreamEngine`** | Past entries must never be overwritten or deleted. Every mutation requires an immutable, tamper-evident sequence history. |
| **Transactional Outbox (Stage-1 simulation)** *(e.g., staging events before Kafka/RabbitMQ)* | **`EventStreamEngine`** | Preserves exact per-aggregate publication order while guaranteeing idempotency against duplicate event emission. |
| **Saga / Process Manager execution logs** *(e.g., multi-step payment orchestration)* | **`EventStreamEngine`** | Tracks which compensation or forward steps have already completed in strict sequence order. |
| **Static configurations & reference catalogs** *(e.g., Dispenser flow rates, feature flags)* | **`StateStoreEngine`** | Only the current active configuration matters. Updating an entry replaces the old snapshot without sequence overhead. |
| **CQRS read projections & materialized views** *(e.g., pre-computed dashboard summaries)* | **`StateStoreEngine`** | Read models are disposable snapshots rebuilt from events; fast key lookup (`findById`) and overwrite semantics (`save`) suffice. |
| **Idempotency / deduplication registries** *(e.g., tracking processed webhook IDs)* | **`StateStoreEngine`** | Provides fast O(1) existence checks (`exists(id)`) without maintaining ordered lists or monotonic sequence counters. |
| **Ephemeral session tokens or heartbeats** *(e.g., active POS terminal status)* | **`StateStoreEngine`** | High-frequency state replacement where only the latest heartbeat or cart snapshot is relevant. |

---

## 🧩 Type Agnosticism & Domain Freedom

Both engines are fully parameterized (`<S, E_ID, E>` and `<ID, T>`) and impose
**zero marker interfaces, base classes, or serialization annotations** on your
domain objects:
- **Any Payload Structure:** Store simple Java `record`s, standard POJOs, or
  polymorphic `sealed interface` hierarchies (e.g.,
  `sealed interface TapEvent permits TapOpened, LiquidDispensed, TapClosed`)
  within the same stream or store.
- **Zero Serialization Overhead:** Objects are held directly by reference in
  memory—no JSON mappers, reflection, or no-arg constructors are needed. For
  complete thread safety, prefer immutable domain types such as Java `record`s.

---

## 🚀 User's Guide

Use the `PersistenceEnginesJdk` facade to instantiate the exact engine required
by your adapter. Each factory call returns a fresh, isolated engine instance.

### 1. Partitioned Event Stream Engine (`EventStreamEngine`)

A thread-safe, append-only event log featuring **per-stream striped
`ReentrantLock` partitions**, **optimistic sequence validation**, **idempotency
deduplication**, and **lock-free immutable snapshot reads**.

```java
import com.sugarcoatlabs.persistence.PersistenceEnginesJdk;
import com.sugarcoatlabs.persistence.event.EventStreamEngine;
import com.sugarcoatlabs.persistence.exception.DuplicateEventException;
import com.sugarcoatlabs.persistence.exception.OptimisticConcurrencyException;

import java.util.List;
import java.util.UUID;

// 1. Obtain a segregated EventStreamEngine via the facade
EventStreamEngine<String, UUID, String> engine =
        PersistenceEnginesJdk.eventStream();

String streamId = "dispenser-01";
UUID eventId = UUID.randomUUID();

// 2. Append atomically with expected sequence validation (1-indexed)
try {
    engine.append(streamId, eventId, 1L, 1L, "TAP_OPENED");
} catch (OptimisticConcurrencyException | DuplicateEventException e) {
    // Translate into your application's domain exception inside your Adapter
}

// 3. Query current sequence and read lock-free immutable stream snapshots
long currentSeq = engine.currentSequence(streamId); // returns 1L
List<String> history = engine.stream(streamId);     // unmodifiable ["TAP_OPENED"]
```

### 2. Mutable State Store Engine (`StateStoreEngine`)

A thread-safe, null-safe key-value state store for configuration registries,
projections, and entity snapshots.

```java
import com.sugarcoatlabs.persistence.PersistenceEnginesJdk;
import com.sugarcoatlabs.persistence.state.StateStoreEngine;

import java.util.List;
import java.util.Optional;

record TapConfig(String id, double flowRate) {}

// 1. Obtain a segregated StateStoreEngine via the facade
StateStoreEngine<String, TapConfig> registry =
        PersistenceEnginesJdk.stateStore();

// 2. Save (idempotent upsert) & query
registry.save("tap-1", new TapConfig("tap-1", 0.064));

Optional<TapConfig> config = registry.findById("tap-1");
boolean exists = registry.exists("tap-1");
List<TapConfig> allConfigs = registry.findAll(); // unmodifiable snapshot
boolean removed = registry.deleteById("tap-1"); // true if entry existed
```

---

## 🏛 Hexagonal Architecture Integration

Keep your application's **Domain Ports** (`TapEventPublishingPort`,
`TapEventFetchingPort`, `TapRegistryPort`) and **Domain Contract Test Suites**
strictly inside your application. Use `persistence-engines-core` exclusively
inside your **Stage-1 Outbound Adapters**:

```java
@Component
@Profile("stage-1")
public class InMemoryTapEventStore
        implements TapEventPublishingPort, TapEventFetchingPort {

    private final EventStreamEngine<String, UUID, TapEvent> engine =
            PersistenceEnginesJdk.eventStream();

    @Override
    public void append(TapEvent event, long expectedSequenceNumber) {
        Objects.requireNonNull(event, "TapEvent must not be null");
        try {
            engine.append(
                event.tapId(),
                event.eventId(),
                event.sequenceNumber(),
                expectedSequenceNumber,
                event
            );
        } catch (OptimisticConcurrencyException | DuplicateEventException ex) {
            throw new SequenceConflictException(
                event.tapId(),
                expectedSequenceNumber,
                engine.currentSequence(event.tapId())
            );
        }
    }

    @Override
    public long getCurrentSequenceNumber(String tapId) {
        return engine.currentSequence(tapId);
    }

    @Override
    public List<TapEvent> streamForTap(String tapId) {
        return engine.stream(tapId);
    }
}
```
---

## 📦 Package Architecture & JPMS Visibility

```text
src/main/java
 ├── module-info.java                        <-- JPMS Encapsulation (Zero external requires)
 └── com.sugarcoatlabs.persistence
      ├── package-info.java
      ├── PersistenceEnginesJdk.java         <-- [Exported] Sealed Entry-Point Facade
      ├── event/
      │    ├── package-info.java
      │    ├── EventStreamEngine.java        <-- [Exported] Append-Only Event Log Contract
      │    └── InMemoryEventStream.java      <-- Encapsulated JDK Implementation
      ├── state/
      │    ├── package-info.java
      │    ├── StateStoreEngine.java         <-- [Exported] Mutable Latest-State Contract
      │    └── InMemoryStateStore.java       <-- Encapsulated JDK Implementation
      ├── exception/
      │    ├── package-info.java
      │    ├── OptimisticConcurrencyException.java <-- [Exported]
      │    └── DuplicateEventException.java        <-- [Exported]
      └── lifecycle/
           ├── package-info.java
           └── ResettableEngine.java         <-- [Unexported] Test Reset Lifecycle Hook
```

---

## 🔌 Build & Dependency Configuration

### Local Build, Test & Javadoc Verification

Run the full Technology Compatibility Kit (TCK) suite, verify module
boundaries, and generate Javadocs:

```bash
mvn clean verify javadoc:javadoc
```

Install into your local `.m2` repository:

```bash
mvn clean install
```

### Client Dependency Declaration

**Maven (`pom.xml`):**
```xml
<dependency>
    <groupId>com.sugarcoatlabs</groupId>
    <artifactId>persistence-engines-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

**Gradle (`build.gradle`):**
```groovy
implementation 'com.sugarcoatlabs:persistence-engines-core:1.0.0-SNAPSHOT'
```

---

## 🧪 Technical Specifications & Concurrency Guarantees

- **Per-Stream Striped `ReentrantLock` Partitions:** Write contention in
  `EventStreamEngine` is isolated per stream key (`S`) using dedicated
  `ReentrantLock` partitions—virtual-thread friendly and free of carrier-thread
  pinning.
- **Atomic Lifecycle Barrier (`ReentrantReadWriteLock`):** `InMemoryEventStream`
  pairs per-stream write locks with a global read-write lifecycle lock so
  concurrent `append()` operations execute in parallel across streams while
  `ResettableEngine#clear()` resets all partitions atomically without racing
  active writes.
- **Atomic Validation Order:** Sequence number checks execute strictly before
  idempotency registration inside the partition lock, preventing failed sequence
  checks from polluting the deduplication registry.
- **Lock-Free Snapshot Reads:** `stream(S)` and `findAll()` return unmodifiable
  snapshots (`List.copyOf(...)`), preventing external callers from mutating
  internal state or triggering `ConcurrentModificationException`.
- **Null & Blank Safety:** All public APIs reject `null` (and blank
  `CharSequence` keys) immediately with `IllegalArgumentException`. Querying
  unknown keys gracefully returns `0L`, `Optional.empty()`, or `List.of()`.
- **Segregated Test Reset Hook (`ResettableEngine`):** Concrete implementations
  implement `ResettableEngine#clear()` inside the non-exported
  `com.sugarcoatlabs.persistence.lifecycle` package for deterministic
  `@BeforeEach` resets in TCK suites without exposing destructive wipe
  operations to production callers.

---

## 📄 License

Licensed under the [Apache License, Version 2.0](LICENSE).
