# Implementation Guide

**One Behavioral Contract, Technology Realizations via TCK.**

`persistence-engines-core` fournit des contrats agnostiques (`EventStreamEngine` et `StateStoreEngine`) validés par un Technology Compatibility Kit (TCK). Les réalisations tierces (DynamoDB, PostgreSQL, etc.) doivent implémenter ces contrats et passer la suite TCK.

---

## 1. Frontières Architecturales et JPMS

- **Contrats publics exportés (`core`) :** `PersistenceEngines`, `PersistencePipeline`, `EventStreamEngine`, `StateStoreEngine`, et les exceptions associées (`OptimisticConcurrencyException`, `DuplicateEventException`).
- **Point d'entrée fluent :** Chaque module fournit un point d'entrée implémentant `PersistencePipeline` (ex. `PersistenceEngines.using(DynamoDb.client(client)).table("...").eventStream(codec)`).
- **Isolation interne :** Les classes concrètes doivent résider dans un sous-package non exporté `.impl` (ex. `com.sugarcoatlabs.persistence.dynamodb.impl`) en évitant les types internes du noyau.

---

## 2. Sémantiques Comportementales Obligatoires

- **Sémantique générale :** Échec rapide (`IllegalArgumentException`) sur les clés ou charges nulles/vides, retours par défaut sécurisés pour les clés absentes, collections non modifiables et forte consistance.
- **`EventStreamEngine` :** Séquences commençant à `1L`, validation avant déduplication globale, gestion de `OptimisticConcurrencyException` et `DuplicateEventException`, doubles écritures atomiques.
- **`StateStoreEngine` :** Upserts idempotents via `save(id, entity)` et retours booléens précis pour `deleteById(id)`.

---

## 3. Réutilisation du TCK et Configuration Maven

- **Exportation TCK :** `persistence-engines-core` produit un artifact `test-jar` filtré sur `com/sugarcoatlabs/persistence/tck/**`.
- **Dépendance consommateur :** Inclure l'artifact core et son `test-jar` en scope `test`, puis étendre `EventStreamEngineContractTest` et `StateStoreEngineContractTest`.

> *Consultez le fichier source complet pour l'implémentation détaillée et le modèle `pom.xml` complet de l'Annexe A.*

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

     - Engine Interfaces:
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
