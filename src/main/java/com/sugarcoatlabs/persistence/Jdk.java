package com.sugarcoatlabs.persistence;

import com.sugarcoatlabs.persistence.event.EventStreamEngine;
import com.sugarcoatlabs.persistence.impl.InMemoryEventStream;
import com.sugarcoatlabs.persistence.impl.InMemoryStateStore;
import com.sugarcoatlabs.persistence.state.StateStoreEngine;

/**
 * Pure-JDK In-Memory Persistence Technology Entry Point
 *
 * <p>Supplies zero-dependency, heap-backed engine realizations for
 * {@link PersistenceEngines#using(PersistencePipeline)}.</p>
 */
public final class Jdk {

    private static final InMemoryPipeline IN_MEMORY = new InMemoryPipeline();

    private Jdk() {
    }

    /**
     * Selects the pure-JDK, thread-safe in-memory realization.
     *
     * @return the terminal in-memory pipeline exposing engine factory methods
     */
    public static InMemoryPipeline inMemory() {
        return IN_MEMORY;
    }

    /**
     * Terminal pipeline stage for pure-JDK in-memory engines.
     */
    public static final class InMemoryPipeline implements PersistencePipeline {

        private InMemoryPipeline() {
        }

        /**
         * Creates a new isolated, thread-safe in-memory {@link EventStreamEngine}.
         *
         * @param <S>    the stream partition key type
         * @param <E_ID> the globally unique event identifier type
         * @param <E>    the domain event payload type
         * @return a fresh in-memory event stream engine instance
         */
        public <S, E_ID, E> EventStreamEngine<S, E_ID, E> eventStream() {
            return new InMemoryEventStream<>();
        }

        /**
         * Creates a new isolated, thread-safe in-memory {@link StateStoreEngine}.
         *
         * @param <ID> the unique entity identifier type
         * @param <T>  the entity or state snapshot type
         * @return a fresh in-memory state store engine instance
         */
        public <ID, T> StateStoreEngine<ID, T> stateStore() {
            return new InMemoryStateStore<>();
        }
    }
}
