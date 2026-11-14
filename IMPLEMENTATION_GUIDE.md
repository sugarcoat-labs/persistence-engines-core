# Implementation Guide

**One Behavioral Contract, Many Technology Realizations.**

`persistence-engines-core` provides technology-agnostic persistence contracts (`EventStreamEngine` and `StateStoreEngine`) verified through a portable Technology Compatibility Kit (TCK). Realizations for platforms like Amazon DynamoDB, PostgreSQL, Oracle, or MongoDB must implement these contracts and pass the inherited TCK suite.

---

## 1. Architectural & JPMS Boundaries

Realizations must adhere to exported public contracts and manage native lifecycles:

- **Allowed Public Contracts (`module-info.java` exports):**
  - `com.sugarcoatlabs.persistence.event.EventStreamEngine`
  - `com.sugarcoatlabs.persistence.state.StateStoreEngine`
  - `com.sugarcoatlabs.persistence.exception.OptimisticConcurrencyException`
  - `com.sugarcoatlabs.persistence.exception.DuplicateEventException`
- **Forbidden Internal Types:** Avoid JDK implementations (`InMemoryEventStream`, `InMemoryStateStore`) and internal lifecycle classes like `ResettableEngine`; handle data resets inside your module's test setup.

---

## 2. Mandatory Behavioral Semantics

Realizations must mirror the invariants of the reference core:

- **General Semantics:** Fail fast on `null` or blank keys/payloads with `IllegalArgumentException`; return safe defaults for absent keys (`0L`, `List.of()`, `Optional.empty()`, `false`); enforce unmodifiable snapshots on collections; maintain strong read-your-writes consistency.
- **`EventStreamEngine` Semantics:** Use 1-indexed sequences starting at `1L`; validate sequence numbers prior to global ID deduplication; throw `OptimisticConcurrencyException` on sequence mismatches and `DuplicateEventException` on duplicate event IDs; execute dual-writes atomically.
- **`StateStoreEngine` Semantics:** Support idempotent upserts via `save(id, entity)` and return accurate boolean results from `deleteById(id)`.

---

## 3. Importing the Contract TCK (`test-jar`)

To reuse the abstract contract test suite:

1. **Core Exporter Setup (`persistence-engines-core/pom.xml`):** Configure `maven-jar-plugin` to generate a `test-jar` containing `com/sugarcoatlabs/persistence/tck/**`.
2. **Consumer Setup (`<module>/pom.xml`):** Include both the core artifact and its `test-jar` with `<scope>test</scope>`, and configure `maven-surefire-plugin` for JPMS module-path execution.

---

## 4. Extending Abstract Contract Suites & Templates

Extend the TCK test classes in your module's `src/test/java` by supplying your engine factory and table/storage reset hooks. A minimal `pom.xml` template for dependent realizations can be configured with the core dependencies, TCK test-jar, and JUnit Jupiter, as seen in the complete reference document.

---

## Annex A: Minimal `pom.xml` Template for Dependent Adapter Projects

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

     - Interfaces:
         * com.sugarcoatlabs.persistence.event.EventStreamEngine
         * com.sugarcoatlabs.persistence.state.StateStoreEngine

     - Exceptions:
         * com.sugarcoatlabs.persistence.exception.OptimisticConcurrencyException
         * com.sugarcoatlabs.persistence.exception.DuplicateEventException
         
     - TCK Abstract Test Suites (imported via `test-jar`):
         * com.sugarcoatlabs.persistence.tck.event.EventStreamEngineContractTest
         * com.sugarcoatlabs.persistence.tck.state.StateStoreEngineContractTest

  2. TO BE IMPLEMENTED IN THIS MODULE (`persistence-engines-dynamodb`):
     - Production (`src/main/java`):

         * module-info.java
             JPMS module descriptor requiring `com.sugarcoatlabs.persistence.core`
             and AWS SDK DynamoDB, exporting only the public facade package while
             encapsulating concrete implementation classes.

         * PersistenceEnginesDynamoDb.java
             Public non-instantiable entry-point facade (mirroring `PersistenceEnginesJdk`)
             that accepts `DynamoDbClient`, table names, and codecs, returning only
             narrow `EventStreamEngine` and `StateStoreEngine` interfaces.

         * DynamoDbEventStream.java
             Package-private realization of `EventStreamEngine` backed by DynamoDB
             `TransactWriteItems` (atomic sequence check + global `eventId` sentinel)
             and `ConsistentRead=true` queries.

         * DynamoDbStateStore.java
             Package-private realization of `StateStoreEngine` backed by DynamoDB
             `PutItem`, `GetItem` (`ConsistentRead=true`), `Scan`, and `DeleteItem`.

     - Integration Tests (`src/test/java`):

         * DynamoDbEventStreamIT.java
             Concrete test class extending `EventStreamEngineContractTest` from the
             imported TCK, provisioning DynamoDB Local / Testcontainers and resetting
             table state between tests to verify 100% behavioral parity.

         * DynamoDbStateStoreIT.java
             Concrete test class extending `StateStoreEngineContractTest` from the
             imported TCK, verifying state store invariants against DynamoDB Local /
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
      <version>${junit.version}</version>
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
