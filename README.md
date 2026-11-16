# persistence-engines-core

Provides thread-safe, in-memory persistence engines. It is a lightweight,
high-performance, zero-dependency pure Java core module. It is part of the
**sugarcoat-labs** ecosystem and is designed for clean integration under the
Hexagonal Architecture (Ports & Adapters) model, serving as a reusable,
realistic, deterministic alternative to brittle mocks in unit tests and
a fast persistence core for integration testing.

Built around the principle of **"One Behavioral Contract, Many Technology Realizations,"**
this module serves as the canonical specification and portable Technology Compatibility
Kit (TCK) for building independent database realization modules (such as Amazon
DynamoDB, PostgreSQL, Oracle, MongoDB, etc.). External modules implement the
core engine interfaces (`EventStreamEngine` and the JSR-107-aligned `StateStoreEngine`),
alongside zero-overhead ergonomic state-store views (`RepositoryStateStore` for
Spring Data conventions and `EntityStateStore` for Jakarta Persistence/JPA
conventions), domain-agnostic exceptions, and abstract TCK contract test suites
(`test-jar`)—ensuring 100% behavioral parity and seamless technology substitution
through the unified `PersistenceEngines.using(...)` entry point.


---

## 📑 Table of Contents

- [📖 Public API Reference](#-public-api-reference)
  - [1. `EventStreamEngine<S, E_ID, E>`](#1-eventstreamengines-e_id-e)
  - [2. `StateStoreEngine<ID, T>`](#2-statestoreengineid-t)
  - [3. `RepositoryStateStore<ID, T>`](#3-repositorystatestoreid-t)
  - [4. `EntityStateStore<ID, T>`](#4-entitystatestoreid-t)
  - [5. Unified Exception Model Across State-Store Views](#5-unified-exception-model-across-state-store-views)
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
  - [3. Ergonomic State-Store Views (`RepositoryStateStore` & `EntityStateStore`)](#3-ergonomic-state-store-views-repositorystatestore--entitystatestore)
- [🏛 Hexagonal Architecture Integration](#-hexagonal-architecture-integration)
- [📦 Package Architecture & JPMS Visibility](#-package-architecture--jpms-visibility)
- [🔌 Build & Dependency Configuration](#-build--dependency-configuration)
  - [Local Build, Test & Javadoc Verification](#local-build-test--javadoc-verification)
  - [Client Dependency Declaration](#client-dependency-declaration)
- [🧪 Technical Specifications & Concurrency Guarantees](#-technical-specifications--concurrency-guarantees)
- [🔌 Building Database Implementations](#-building-database-implementations)
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

    /** Atomically stores the snapshot only if no entry exists for id. */
    void putIfAbsent(ID id, T entity);

    /** Atomically replaces the existing snapshot associated with id. */
    void replace(ID id, T entity);

    /** Atomically stores or overwrites the snapshot associated with id. */
    void put(ID id, T entity);

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

- **`putIfAbsent(id, entity)`** atomically inserts a new entry only if `id` is
  absent, throwing `DuplicateEntityException` if an entry already exists.
- **`replace(id, entity)`** atomically overwrites the snapshot for an existing
  `id`, throwing `EntityNotFoundException` if no entry exists.
- **`put(id, entity)`** performs an atomic, idempotent upsert that stores a new
  entry or replaces any existing snapshot for `id`.
- **`findById(id)`** retrieves the current snapshot wrapped in an `Optional`,
  never returning `null`.
- **`findAll()`** returns an unmodifiable point-in-time list of all stored
  entities (`List.of()` when empty).
- **`exists(id)`** checks key presence in \(O(1)\) time without allocating or
  copying snapshots.
- **`deleteById(id)`** atomically removes the entry for `id` and reports
  whether an entry was actually deleted.

### 3. `RepositoryStateStore<ID, T>`
*Package: `com.sugarcoatlabs.persistence.state`*

```java
public interface RepositoryStateStore<ID, T> {

    static <ID, T> RepositoryStateStore<ID, T> adapting(StateStoreEngine<ID, T> delegate);

    void insert(ID id, T entity);
    void update(ID id, T entity);
    void save(ID id, T entity);
    Optional<T> findById(ID id);
    List<T> findAll();
    boolean existsById(ID id);
    boolean deleteById(ID id);
}
```

- **`adapting(delegate)`** wraps any `StateStoreEngine<ID, T>` realization
  (in-memory or external database) in a zero-overhead Spring Data repository-style
  facade.
- **`insert` / `update` / `save`** delegate atomically to `putIfAbsent`,
  `replace`, and `put` respectively, preserving `DuplicateEntityException` and
  `EntityNotFoundException` guarantees.

### 4. `EntityStateStore<ID, T>`
*Package: `com.sugarcoatlabs.persistence.state`*

```java
public interface EntityStateStore<ID, T> {

    static <ID, T> EntityStateStore<ID, T> adapting(StateStoreEngine<ID, T> delegate);

    void persist(ID id, T entity);
    void replace(ID id, T entity);
    void merge(ID id, T entity);
    Optional<T> find(ID id);
    List<T> findAll();
    boolean contains(ID id);
    boolean remove(ID id);
}
```

- **`adapting(delegate)`** wraps any `StateStoreEngine<ID, T>` realization in a
  zero-overhead Jakarta Persistence (`EntityManager`-style) facade.
- **`persist` / `replace` / `merge`** delegate atomically to `putIfAbsent`,
  `replace`, and `put` respectively, preserving `DuplicateEntityException` and
  `EntityNotFoundException` guarantees.


### 5. Unified Exception Model Across State-Store Views
*Package: `com.sugarcoatlabs.persistence.exception`*

All three state-store interfaces (`StateStoreEngine`, `RepositoryStateStore`, and
`EntityStateStore`) share a single, framework-neutral runtime exception model
rather than maintaining separate exception hierarchies per view:

| Failure Condition | `persistence-engines-core` | JSR-107 (`javax.cache`) | Jakarta Persistence (JPA) | Spring Data (`org.springframework.dao`) |
| :--- | :--- | :--- | :--- | :--- |
| **Key already exists on conditional insert** | **`DuplicateEntityException`** | *(Returns `false` on `putIfAbsent`)* | `EntityExistsException` | `DuplicateKeyException` |
| **Key absent on conditional update/replace** | **`EntityNotFoundException`** | *(Returns `false` on `replace`)* | **`EntityNotFoundException`** | `EmptyResultDataAccessException` |
| **Null or blank arguments** | **`IllegalArgumentException`** | `NullPointerException` | `IllegalArgumentException` | `IllegalArgumentException` |

- **Zero Translation Overhead:** `RepositoryStateStore` and `EntityStateStore`
  delegate directly to `StateStoreEngine` without catching, wrapping, or
  re-throwing exceptions.
- **Consistent Hexagonal Adapter Boundaries:** Outbound adapters catch the exact
  same `DuplicateEntityException` and `EntityNotFoundException` types (and
  inspect `.id()`) regardless of which state-store interface convention is
  selected.
- **Classpath Isolation:** Avoids colliding with Spring or Jakarta exception
  class names while keeping `persistence-engines-core` free of external
  dependencies.


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
| **PostgreSQL** *(Relational)* | `INSERT` (SQLSTATE `23505` &rarr; `DuplicateEntityException`) for `putIfAbsent()`; `UPDATE ... WHERE id = ?` (`affectedRows == 0` &rarr; `EntityNotFoundException`) for `replace()`; `INSERT ... ON CONFLICT (id) DO UPDATE` for `put()`; `DELETE ... RETURNING 1` for `deleteById()`. | None. Full ACID parity out of the box. |
| **MySQL 8.0+** *(InnoDB)* | `INSERT` (Error `1062` &rarr; `DuplicateEntityException`) for `putIfAbsent()`; `UPDATE ... WHERE id = ?` for `replace()`; `INSERT ... ON DUPLICATE KEY UPDATE` for `put()`; `DELETE FROM ... WHERE id = ?` for `deleteById()`. | Requires **InnoDB**. Ensure `replace()` does not throw `EntityNotFoundException` when updating an existing row with an identical payload (`useAffectedRows=false` or version/timestamp touch). |
| **Amazon DynamoDB** *(Key-Value)* | `PutItem` with `attribute_not_exists(PK)` for `putIfAbsent()`; `PutItem` with `attribute_exists(PK)` for `replace()`; unconditional `PutItem` for `put()`; `DeleteItem` (`ReturnValues: ALL_OLD`) for `deleteById()`. | Use `ConsistentRead=true` on `GetItem` if strict read-your-writes consistency is required; `findAll()` (`Scan`) is eventually consistent. |
| **MongoDB** *(Document)* | `insertOne` (`E11000` &rarr; `DuplicateEntityException`) for `putIfAbsent()`; `replaceOne({ _id: id }, doc, { upsert: false })` (`matchedCount == 0` &rarr; `EntityNotFoundException`) for `replace()`; `replaceOne(..., { upsert: true })` for `put()`. | Check `matchedCount == 0` (not `modifiedCount == 0`) on `replace()` so identical payloads succeed. Configure `WriteConcern.MAJORITY` and `ReadConcern.MAJORITY`. |
| **KurrentDB** *(EventStoreDB)* | Snapshot stream per entity (`MaxCount = 1`) or dedicated read-model projection store. | Not a native key-value store; prefer a relational or document store for `StateStoreEngine` adapters. |

- **Conditional Writes vs. Optimistic Versioning:** `StateStoreEngine` supports
  atomic existence preconditions (`putIfAbsent` and `replace`) as well as
  idempotent overwrites (`put`), but deliberately omits payload-based
  Compare-And-Swap (`replace(id, oldEntity, newEntity)`). If your entity
  requires monotonic version checks on updates, model it as an event-sourced
  aggregate on `EventStreamEngine` instead.

  ## 🎯 Core Philosophy

This module is built strictly for **Java 24+** (with full JPMS `module-info.java`
encapsulation) and deliberately excludes external frameworks (Spring, Micronaut,
Quarkus), database drivers, and test libraries from its production bytecode.

It serves as a **zero-dependency, pure-JDK persistence core** designed around
strict architectural boundaries:
- **Unified Entry Point & Interface Segregation (ISP):** A single universal
  entry point (`PersistenceEngines.using(...)`) accepts technology-specific
  pipelines (`PersistencePipeline`, such as `Jdk.inMemory()`) and provides
  direct access to completely independent, narrowly scoped engines
  (`EventStreamEngine`, `StateStoreEngine`). Consumers only depend on the exact
  behavioral contract they need.
- **Ergonomic Adapter Views Without Implementation Duplication:** Database
  realizations implement only the single canonical `StateStoreEngine<ID, T>`
  contract, while zero-overhead pure-JDK facades (`RepositoryStateStore` and
  `EntityStateStore`) adapt any engine instance into Spring Data or Jakarta
  Persistence (JPA) conventions.
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
  concurrency, idempotency, atomic conditional writes, and immutability rules
  out of the box—matching the consistency guarantees of real databases without
  booting external infrastructure during fast unit testing or local prototyping.

---

## 🧠 Behavioral Model: Event History vs. Current State

While both engines organize data by key under the hood, they enforce
fundamentally different **data lifecycle semantics**:

| Engine | Behavioral Model | Write Semantics | Concurrency Guarantees |
| :--- | :--- | :--- | :--- |
| **`EventStreamEngine`**<br>*(Append-Only Log)* | **Immutable History**<br>(`StreamId → List<Event>`) | Strictly **append-only**. Past facts are never overwritten or deleted. | Per-stream `ReentrantLock` partitions, global `ReentrantReadWriteLock` barrier, monotonic sequence checks, and `eventId` deduplication. |
| **`StateStoreEngine`**<br>*(Entity / Registry Store)* | **Mutable Latest State**<br>(`EntityId → Entity`) | **Conditional & Upsert Writes** (`putIfAbsent`, `replace`, `put`). | Lock-free atomic updates via `ConcurrentHashMap` with unmodifiable collection read boundaries. |

### Which Engine Should I Choose?

| Use Case | Recommended Engine | Why |
| :--- | :--- | :--- |
| **Event-Sourced aggregates & domain workflows** *(e.g., Order Placed → Paid → Shipped)* | **`EventStreamEngine`** | Current state is derived by replaying ordered facts. Optimistic locking prevents concurrent commands from corrupting aggregate state. |
| **Financial ledgers, billing & audit trails** *(e.g., wallet credits/debits, compliance logs)* | **`EventStreamEngine`** | Past entries must never be overwritten or deleted. Every mutation requires an immutable, tamper-evident sequence history. |
| **Transactional Outbox (Stage-1 simulation)** *(e.g., staging events before Kafka/RabbitMQ)* | **`EventStreamEngine`** | Preserves exact per-aggregate publication order while guaranteeing idempotency against duplicate event emission. |
| **Saga / Process Manager execution logs** *(e.g., multi-step payment orchestration)* | **`EventStreamEngine`** | Tracks which compensation or forward steps have already completed in strict sequence order. |
| **Static configurations & reference catalogs** *(e.g., Dispenser flow rates, feature flags)* | **`StateStoreEngine`** | Enforces atomic duplicate prevention on registration (`putIfAbsent`) and safe updates (`replace`) without sequence overhead. |
| **CQRS read projections & materialized views** *(e.g., pre-computed dashboard summaries)* | **`StateStoreEngine`** | Read models are disposable snapshots rebuilt from events; fast key lookup (`findById`) and idempotent upsert (`put`) suffice. |
| **Idempotency / deduplication registries** *(e.g., tracking processed webhook IDs)* | **`StateStoreEngine`** | Provides atomic insert-once registration (`putIfAbsent`) and O(1) existence checks (`exists(id)`). |
| **Ephemeral session tokens or heartbeats** *(e.g., active POS terminal status)* | **`StateStoreEngine`** | High-frequency state replacement (`put` / `replace`) where only the latest heartbeat or cart snapshot is relevant. |

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

Use the unified `PersistenceEngines.using(...)` entry point with `Jdk.inMemory()`
to instantiate the exact engine required by your adapter. Each pipeline call
returns a fresh, isolated engine instance.

### 1. Partitioned Event Stream Engine (`EventStreamEngine`)

A thread-safe, append-only event log featuring **per-stream striped
`ReentrantLock` partitions**, **optimistic sequence validation**, **idempotency
deduplication**, and **lock-free immutable snapshot reads**.

```java
import com.sugarcoatlabs.persistence.Jdk;
import com.sugarcoatlabs.persistence.PersistenceEngines;
import com.sugarcoatlabs.persistence.event.EventStreamEngine;
import com.sugarcoatlabs.persistence.exception.DuplicateEventException;
import com.sugarcoatlabs.persistence.exception.OptimisticConcurrencyException;

import java.util.List;
import java.util.UUID;

// 1. Obtain a segregated EventStreamEngine via the unified entry point
EventStreamEngine<String, UUID, String> engine =
        PersistenceEngines.using(Jdk.inMemory()).eventStream();

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

A thread-safe, null-safe key-value state store following JSR-107 /
`ConcurrentMap` semantics for configuration registries, projections, and entity
snapshots.

```java
import com.sugarcoatlabs.persistence.Jdk;
import com.sugarcoatlabs.persistence.PersistenceEngines;
import com.sugarcoatlabs.persistence.exception.DuplicateEntityException;
import com.sugarcoatlabs.persistence.exception.EntityNotFoundException;
import com.sugarcoatlabs.persistence.state.StateStoreEngine;

import java.util.List;
import java.util.Optional;

record TapConfig(String id, double flowRate) {}

// 1. Obtain a segregated StateStoreEngine via the unified entry point
StateStoreEngine<String, TapConfig> registry =
        PersistenceEngines.using(Jdk.inMemory()).stateStore();

// 2. Conditional insert (putIfAbsent), conditional update (replace), and upsert (put)
registry.putIfAbsent("tap-1", new TapConfig("tap-1", 0.064)); // throws DuplicateEntityException if exists
registry.replace("tap-1", new TapConfig("tap-1", 0.128));     // throws EntityNotFoundException if missing
registry.put("tap-2", new TapConfig("tap-2", 0.256));         // idempotent last-write-wins upsert

// 3. Query & delete
Optional<TapConfig> config = registry.findById("tap-1");
boolean exists = registry.exists("tap-1");
List<TapConfig> allConfigs = registry.findAll(); // unmodifiable snapshot
boolean removed = registry.deleteById("tap-1"); // true if entry existed
```

### 3. Ergonomic State-Store Views (`RepositoryStateStore` & `EntityStateStore`)

Adapt any `StateStoreEngine` realization (in-memory or external database) into
Spring Data repository or Jakarta Persistence (`EntityManager`) conventions
without altering the underlying storage engine:

```java
import com.sugarcoatlabs.persistence.Jdk;
import com.sugarcoatlabs.persistence.PersistenceEngines;
import com.sugarcoatlabs.persistence.state.EntityStateStore;
import com.sugarcoatlabs.persistence.state.RepositoryStateStore;
import com.sugarcoatlabs.persistence.state.StateStoreEngine;

StateStoreEngine<String, TapConfig> baseStore =
        PersistenceEngines.using(Jdk.inMemory()).stateStore();

// 1. Spring Data Repository-style facade
RepositoryStateStore<String, TapConfig> repoView =
        RepositoryStateStore.adapting(baseStore);

repoView.insert("tap-1", new TapConfig("tap-1", 0.064));
repoView.update("tap-1", new TapConfig("tap-1", 0.128));
repoView.save("tap-2", new TapConfig("tap-2", 0.256));
boolean existsById = repoView.existsById("tap-1");

// 2. Jakarta Persistence (JPA) EntityManager-style facade
EntityStateStore<String, TapConfig> jpaView =
        EntityStateStore.adapting(baseStore);

jpaView.persist("tap-3", new TapConfig("tap-3", 0.512));

jpaView.replace("tap-3", new TapConfig("tap-3", 0.640));
jpaView.merge("tap-4", new TapConfig("tap-4", 0.768));
boolean contains = jpaView.contains("tap-3");
jpaView.remove("tap-3");
```

## 🏛 Hexagonal Architecture Integration

Keep your application's **Domain Ports** (`TapEventPublishingPort`,
`TapEventFetchingPort`, `TapRegistryPort`) and **Domain Contract Test Suites**
strictly inside your application. Use `persistence-engines-core` exclusively
inside your **Stage-1 Outbound Adapters**:

### 1. Event Store Adapter (`EventStreamEngine`)

An event store adapter bridges your domain ports (`TapEventPublishingPort`,
`TapEventFetchingPort`) to `EventStreamEngine<String, UUID, TapEvent>`,
extracting the stream partition key (`event.tapId()`) and translating core
persistence exceptions into domain-specific errors at the adapter boundary:

```java
@Component
@Profile("stage-1")
public class InMemoryTapEventStore
        implements TapEventPublishingPort, TapEventFetchingPort {

    private final EventStreamEngine<String, UUID, TapEvent> engine =
            PersistenceEngines.using(Jdk.inMemory()).eventStream();

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

### 2. Configuration Registry Adapter (`StateStoreEngine`)

Similarly, a registry adapter bridges your domain port (`TapRegistryPort`) to
`StateStoreEngine<String, Tap>`, using atomic `putIfAbsent` and translating
`DuplicateEntityException` into a domain-specific exception without non-atomic
check-then-act guards:

```java
@Component
@Profile("stage-1")
public class InMemoryTapRegistry implements TapRegistryPort {

    private final StateStoreEngine<String, Tap> engine =
            PersistenceEngines.using(Jdk.inMemory()).stateStore();

    @Override
    public void save(Tap tap) {
        Objects.requireNonNull(tap, "Tap must not be null");
        try {
            engine.putIfAbsent(tap.id(), tap);
        } catch (DuplicateEntityException ex) {
            throw new DuplicateTapException(tap.id());
        }
    }

    @Override
    public Optional<Tap> findById(String id) {
        return engine.findById(id);
    }

    @Override
    public List<Tap> findAll() {
        return engine.findAll();
    }

    @Override
    public boolean exists(String id) {
        return engine.exists(id);
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
      ├── PersistenceEngines.java            <-- [Exported] Universal Entry-Point Interface
      ├── PersistencePipeline.java           <-- [Exported] Technology Pipeline Marker
      ├── Jdk.java                           <-- [Exported] Pure-JDK Pipeline Realization
      ├── event/
      │    ├── package-info.java
      │    └── EventStreamEngine.java        <-- [Exported] Append-Only Event Log Contract
      ├── state/
      │    ├── package-info.java
      │    ├── StateStoreEngine.java         <-- [Exported] Canonical JSR-107 State Contract
      │    ├── RepositoryStateStore.java     <-- [Exported] Spring Data Repository-Style View
      │    └── EntityStateStore.java         <-- [Exported] Jakarta Persistence (JPA)-Style View
      ├── exception/
      │    ├── package-info.java
      │    ├── OptimisticConcurrencyException.java <-- [Exported]
      │    ├── DuplicateEventException.java        <-- [Exported]
      │    ├── DuplicateEntityException.java       <-- [Exported]
      │    └── EntityNotFoundException.java        <-- [Exported]
      ├── impl/
      │    ├── package-info.java
      │    ├── InMemoryEventStream.java      <-- [Unexported] Pure-JDK Event Stream Realization
      │    └── InMemoryStateStore.java       <-- [Unexported] Pure-JDK State Store Realization
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
- **Atomic Conditional State Writes:** `InMemoryStateStore` delegates
  `putIfAbsent` and `replace` directly to `ConcurrentHashMap#putIfAbsent` and
  `ConcurrentHashMap#replace`, eliminating check-then-act race conditions.
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

## 🔌 Building Database Implementations

**One Behavioral Contract, Many Technology Realizations.**

`persistence-engines-core` serves as the foundational specification and Technology Compatibility Kit (TCK) for the **sugarcoat-labs** persistence ecosystem. External technology realizations (such as `persistence-engines-dynamodb`, `persistence-engines-postgres`, or `persistence-engines-mongodb`) implement the exported engine interfaces and import the filtered TCK `test-jar` (`com.sugarcoatlabs.persistence.tck.**`) to verify 100% behavioral parity without duplicating contract tests.

See the **[Implementation Guide](IMPLEMENTATION_GUIDE.md)** for full details on:
- JPMS visibility boundaries and forbidden internal types
- Mandatory behavioral invariants (OCC, global idempotency, atomic conditional writes, 1-indexed sequences, read-your-writes consistency)
- Maven `test-jar` export/import configuration and minimal `pom.xml` templates

---

## 📄 License

Licensed under the [Apache License, Version 2.0](LICENSE).
