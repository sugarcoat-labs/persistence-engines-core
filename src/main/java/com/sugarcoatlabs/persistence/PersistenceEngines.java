package com.sugarcoatlabs.persistence;

import com.sugarcoatlabs.persistence.event.EventStreamEngine;
import com.sugarcoatlabs.persistence.state.EntityStateStore;
import com.sugarcoatlabs.persistence.state.RepositoryStateStore;
import com.sugarcoatlabs.persistence.state.StateStoreEngine;

/**
 * Universal Entry Point for Persistence Engines.
 *
 * <p>Provides a technology-agnostic starting point via {@link #using(PersistencePipeline)}
 * to configure and instantiate persistence engines for pure-JDK in-memory use or external
 * databases (Amazon DynamoDB, PostgreSQL, MySQL, MongoDB).</p>
 *
 * <h2>Core Architectural Philosophy</h2>
 * <p>Built around <strong>"One Behavioral Contract, Many Technology Realizations"</strong>,
 * decoupling Hexagonal Architecture adapters from infrastructure:</p>
 * <ul>
 *   <li><strong>Zero-Dependency Pure-JDK Core:</strong> Thread-safe, standard-Java alternative to mocks for testing.</li>
 *   <li><strong>Interface Segregation (ISP):</strong> Exposes only required behavioral contracts, hiding internal lifecycle methods.</li>
 *   <li><strong>100% TCK-Verified Parity:</strong> External pipelines share portable Technology Compatibility Kit contracts.</li>
 * </ul>
 *
 * <h2>Engine Paradigms &amp; State-Store Styles</h2>
 * <ul>
 *   <li><strong>{@link EventStreamEngine}:</strong> Append-only immutable event histories with per-stream OCC and idempotency.</li>
 *   <li><strong>{@link StateStoreEngine}:</strong> Canonical thread-safe key-value snapshots following JSR-107 /
 *       {@link java.util.concurrent.ConcurrentMap} semantics ({@code putIfAbsent}, {@code replace}, {@code put}).</li>
 *   <li><strong>Ergonomic State-Store Facades:</strong> Zero-dependency adapters providing Spring Data repository
 *       style ({@link RepositoryStateStore}) and Jakarta Persistence (JPA) style ({@link EntityStateStore})
 *       over any {@link StateStoreEngine}.</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 * <pre>{@code
 * // 1. Pure-JDK in-memory realizations
 * EventStreamEngine<String, UUID, String> jdkStream =
 *         PersistenceEngines.using(Jdk.inMemory()).eventStream();
 *
 * StateStoreEngine<String, TapConfig> jdkState =
 *         PersistenceEngines.using(Jdk.inMemory()).stateStore();
 *
 * // 2. External database realizations (e.g., DynamoDB)
 * EventStreamEngine<String, UUID, MyEvent> dynamoStream =
 *         PersistenceEngines.using(DynamoDb.client(client))
 *                 .table("events")
 *                 .eventStream(stringCodec, uuidCodec, eventCodec);
 *
 * StateStoreEngine<String, TapConfig> dynamoState =
 *         PersistenceEngines.using(DynamoDb.client(client))
 *                 .table("configs")
 *                 .stateStore(stringCodec, stateCodec);
 *
 * // 3. Ergonomic Spring Data and JPA views over any StateStoreEngine
 * RepositoryStateStore<String, TapConfig> repoStore =
 *         RepositoryStateStore.adapting(jdkState);
 *
 * EntityStateStore<String, TapConfig> jpaStore =
 *         EntityStateStore.adapting(dynamoState);
 * }</pre>
 *
 * @see PersistencePipeline
 * @see Jdk
 * @see EventStreamEngine
 * @see StateStoreEngine
 * @see RepositoryStateStore
 * @see EntityStateStore
 */
public interface PersistenceEngines {

    /**
     * Initiates a fluent engine configuration pipeline for the specified technology.
     *
     * <pre>{@code
     * // 1. Pure-JDK in-memory realizations
     * EventStreamEngine<String, UUID, String> jdkStream =
     *         PersistenceEngines.using(Jdk.inMemory()).eventStream();
     *
     * StateStoreEngine<String, TapConfig> jdkState =
     *         PersistenceEngines.using(Jdk.inMemory()).stateStore();
     *
     * // 2. External database realizations (e.g., DynamoDB)
     * EventStreamEngine<String, UUID, MyEvent> dynamoStream =
     *         PersistenceEngines.using(DynamoDb.client(client))
     *                 .table("events")
     *                 .eventStream(stringCodec, uuidCodec, eventCodec);
     *
     * StateStoreEngine<String, TapConfig> dynamoState =
     *         PersistenceEngines.using(DynamoDb.client(client))
     *                 .table("configs")
     *                 .stateStore(stringCodec, stateCodec);
     * }</pre>
     *
     * @param pipeline the technology-specific pipeline or factory (must not be {@code null})
     * @param <P>      the concrete pipeline type preserved through the fluent chain
     * @return the validated pipeline instance
     * @throws IllegalArgumentException if {@code pipeline} is {@code null}
     */
    static <P extends PersistencePipeline> P using(P pipeline) {
        if (pipeline == null) {
            throw new IllegalArgumentException("Persistence pipeline must not be null");
        }
        return pipeline;
    }
}
