package com.sugarcoatlabs.persistence;

/**
 * Universal Entry Point for Persistence Engines
 *
 * <p>Provides a single, technology-agnostic starting point
 * ({@link #using(PersistencePipeline)}) for configuring and instantiating
 * {@link com.sugarcoatlabs.persistence.event.EventStreamEngine} and
 * {@link com.sugarcoatlabs.persistence.state.StateStoreEngine} realizations across
 * pure-JDK and external database modules.</p>
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
     *                 .eventStream(eventCodec);
     *
     * StateStoreEngine<String, TapConfig> dynamoState =
     *         PersistenceEngines.using(DynamoDb.client(client))
     *                 .table("configs")
     *                 .stateStore(stateCodec);
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
