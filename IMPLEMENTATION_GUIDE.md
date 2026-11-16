# Implementation Guide

**One Behavioral Contract, Many Technology Realizations via TCK.**

`persistence-engines-core` provides domain-agnostic persistence contracts (`EventStreamEngine` and the JSR-107-aligned `StateStoreEngine`, plus zero-overhead Spring Data and JPA ergonomic views) validated by a portable Technology Compatibility Kit (TCK). Third-party technology realizations (Amazon DynamoDB, PostgreSQL, MySQL, MongoDB, etc.) implement the canonical engine interfaces and verify 100% behavioral parity against the TCK suite.

---

## 1. Architectural & JPMS Boundaries

- **Exported Public Contracts (`core`):**
    - Entry point & pipeline: `PersistenceEngines`, `PersistencePipeline`
    - Engine contracts: `EventStreamEngine`, `StateStoreEngine`
    - Ergonomic state-store views: `RepositoryStateStore` (Spring Data style), `EntityStateStore` (JPA style)
    - Domain-agnostic exceptions: `OptimisticConcurrencyException`, `DuplicateEventException`, `DuplicateEntityException`, `EntityNotFoundException`
- **Single-Engine Realization Rule:** External database modules **only implement `StateStoreEngine` and `EventStreamEngine`**. They never need to implement `RepositoryStateStore` or `EntityStateStore`, because those facades wrap any `StateStoreEngine` automatically via `RepositoryStateStore.adapting(...)` and `EntityStateStore.adapting(...)`.
- **Fluent Entry Point:** Each technology module exposes an entry point implementing `PersistencePipeline` (e.g., `PersistenceEngines.using(DynamoDb.client(client)).table("...").eventStream(codec)`).
- **Internal Encapsulation:** Concrete implementations must reside in an unexported `.impl` subpackage (e.g., `com.sugarcoatlabs.persistence.dynamodb.impl`) without exposing internal core types.

---

## 2. Mandatory Behavioral Semantics

- **General Invariants:** Fail-fast validation (`IllegalArgumentException`) on `null` or blank keys and `null` entities, safe empty defaults (`0L`, `Optional.empty()`, `List.of()`) for missing keys, unmodifiable snapshot collections, and read-your-writes consistency.
- **`EventStreamEngine`:** 1-indexed sequences (`0L` = empty stream), sequence validation strictly before global `eventId` deduplication, atomic dual-write enforcement, and accurate translation into `OptimisticConcurrencyException` and `DuplicateEventException`.
- **`StateStoreEngine` (JSR-107 / `ConcurrentMap` Semantics):**
    - **`putIfAbsent(id, entity)`:** Atomically inserts only if `id` does not exist; throws `DuplicateEntityException(id)` on key collision (e.g., DynamoDB `attribute_not_exists(PK)`, SQL `INSERT` unique constraint `23505`/`1062`, MongoDB `insertOne` `E11000`).
    - **`replace(id, entity)`:** Atomically updates only if `id` already exists; throws `EntityNotFoundException(id)` if absent (e.g., DynamoDB `attribute_exists(PK)`, SQL `UPDATE ... WHERE id = ?`, MongoDB `replaceOne(..., { upsert: false })`).
    - **Identical-Payload `replace()` Rule:** Replacing an existing key with a payload equal to the stored value **must succeed**. SQL adapters (especially MySQL) must not confuse `0` modified rows on an existing row with a missing key, and MongoDB adapters must check `matchedCount == 0` rather than `modifiedCount == 0`.
    - **`put(id, entity)`:** Atomically inserts or overwrites (idempotent last-write-wins upsert).
    - **`deleteById(id)`:** Atomically deletes the entry and returns `true` only if an entry was actually removed.

---

## 3. TCK Reuse & Maven Configuration

- **TCK Export:** `persistence-engines-core` publishes a `test-jar` filtered on `com/sugarcoatlabs/persistence/tck/**` containing `EventStreamEngineContractTest`, `StateStoreEngineContractTest`, `RepositoryStateStoreContractTest`, and `EntityStateStoreContractTest`.
- **Consumer Dependency:** Import `persistence-engines-core` alongside its `test-jar` in `test` scope, then extend `EventStreamEngineContractTest` and `StateStoreEngineContractTest` (and optionally `RepositoryStateStoreContractTest` / `EntityStateStoreContractTest` wrapping your concrete `StateStoreEngine`).

---

## Annex A: Minimal `pom.xml` Template for Dependent Realization Projects

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://apache.org"
         xmlns:xsi="http://w3.org"
         xsi:schemaLocation="http://apache.org https://apache.org">
  <modelVersion>4.0.0</modelVersion>

  <groupId>com.sugarcoatlabs</groupId>
  <artifactId>persistence-engines-dynamodb</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <!--
  ============================================================================
  TECHNOLOGY REALIZATION SPECIFICATION & REUSE CONTRACT
  ============================================================================

  1. IMPORTED & REUSED FROM `persistence-engines-core` (DO NOT REWRITE):

     - Entry-Point & Pipeline Contracts:
         * com.sugarcoatlabs.persistence.PersistenceEngines
         * com.sugarcoatlabs.persistence.PersistencePipeline

     - Engine Interfaces & Ergonomic State-Store Views:
         * com.sugarcoatlabs.persistence.event.EventStreamEngine
         * com.sugarcoatlabs.persistence.state.StateStoreEngine
         * com.sugarcoatlabs.persistence.state.RepositoryStateStore
         * com.sugarcoatlabs.persistence.state.EntityStateStore

     - Exceptions:
         * com.sugarcoatlabs.persistence.exception.OptimisticConcurrencyException
         * com.sugarcoatlabs.persistence.exception.DuplicateEventException
         * com.sugarcoatlabs.persistence.exception.DuplicateEntityException
         * com.sugarcoatlabs.persistence.exception.EntityNotFoundException
         
     - TCK Abstract Test Suites (imported via `test-jar`):
         * com.sugarcoatlabs.persistence.tck.EventStreamEngineContractTest
         * com.sugarcoatlabs.persistence.tck.StateStoreEngineContractTest
         * com.sugarcoatlabs.persistence.tck.RepositoryStateStoreContractTest
         * com.sugarcoatlabs.persistence.tck.EntityStateStoreContractTest

  2. TO BE IMPLEMENTED IN THIS MODULE (`persistence-engines-dynamodb`):
     - Production (`src/main/java`):

         * module-info.java
             JPMS module descriptor requiring `com.sugarcoatlabs.persistence.core`
             and AWS SDK DynamoDB, exporting only `com.sugarcoatlabs.persistence.dynamodb`
             while keeping `.impl` unexported.

         * com/sugarcoatlabs/persistence/dynamodb/DynamoDb.java
             Public technology entry point implementing `PersistencePipeline`
             for `PersistenceEngines.using(DynamoDb.client(client))...`, returning
             only narrow `EventStreamEngine` and `StateStoreEngine` interfaces.

         * com/sugarcoatlabs/persistence/dynamodb/impl/DynamoDbEventStream.java
             Encapsulated realization of `EventStreamEngine` backed by DynamoDB
             `TransactWriteItems` (atomic sequence check + global `eventId` sentinel)
             and `ConsistentRead=true` queries.

         * com/sugarcoatlabs/persistence/dynamodb/impl/DynamoDbStateStore.java
             Encapsulated realization of `StateStoreEngine` backed by DynamoDB
             `PutItem` (`attribute_not_exists(PK)` for `putIfAbsent`,
             `attribute_exists(PK)` for `replace`, unconditional for `put`),
             `GetItem` (`ConsistentRead=true`), `Scan`, and `DeleteItem`.

     - Integration Tests (`src/test/java`):

         * DynamoDbEventStreamIT.java
             Concrete test class extending `EventStreamEngineContractTest` from the
             imported TCK, provisioning DynamoDB Local / Testcontainers and resetting
             table state between tests to verify 100% behavioral parity.

         * DynamoDbStateStoreIT.java
             Concrete test class extending `StateStoreEngineContractTest` from the
             imported TCK, verifying state store invariants (including concurrent
             `putIfAbsent` races and conditional `replace`) against DynamoDB Local /
             Testcontainers.

  ============================================================================
  -->
  <properties>
    <maven.compiler.release>24</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <junit.version>5.11.4</junit.version>
  </properties>

  <dependencies>
    <dependency>
      <groupId>com.sugarcoatlabs</groupId>
      <artifactId>persistence-engines-core</artifactId>
      <version>1.0.0-SNAPSHOT</version>
    </dependency>
    <dependency>
      <groupId>com.sugarcoatlabs</groupId>
      <artifactId>persistence-engines-core</artifactId>
      <version>1.0.0-SNAPSHOT</version>
      <type>test-jar</type>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <version>\${junit.version}</version>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <version>3.13.0</version>
      </plugin>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-plugin</artifactId>
        <version>3.5.2</version>
        <configuration>
          <argLine>--add-reads com.sugarcoatlabs.persistence.dynamodb=ALL-UNNAMED</argLine>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```
