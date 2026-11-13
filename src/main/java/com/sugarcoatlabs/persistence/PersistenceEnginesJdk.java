package com.sugarcoatlabs.persistence;

import com.sugarcoatlabs.persistence.event.EventStreamEngine;
import com.sugarcoatlabs.persistence.event.InMemoryEventStream;
import com.sugarcoatlabs.persistence.state.InMemoryStateStore;
import com.sugarcoatlabs.persistence.state.StateStoreEngine;

/**
 * JDK Persistence Engines Facade
 *
 * <p>The single, zero-dependency entry point for obtaining pure-JDK, thread-safe,
 * in-memory persistence engines tailored for Hexagonal Architecture (Ports &amp; Adapters).</p>
 *
 * <p>Each static factory method returns a fresh, isolated, and narrowly scoped engine
 * instance adhering to the Interface Segregation Principle (ISP):</p>
 * <ul>
 *   <li>{@link #eventStream()} – Append-only partitioned event log with optimistic
 *       concurrency control and idempotency deduplication.</li>
 *   <li>{@link #stateStore()} – Mutable latest-state key-value store for entity
 *       snapshots, configurations, and read projections.</li>
 * </ul>
 *
 * <p>Usage Example</p>
 * <pre>{@code
 * // 1. Append-Only Event Stream Engine (for Event-Sourced aggregates & audit logs)
 * EventStreamEngine<String, UUID, String> eventEngine = PersistenceEnginesJdk.eventStream();
 * UUID eventId = UUID.randomUUID();
 * eventEngine.append("tap-01", eventId, 1L, 1L, "TAP_OPENED");
 *
 * long currentSeq = eventEngine.currentSequence("tap-01"); // 1L
 * List<String> history = eventEngine.stream("tap-01");     // ["TAP_OPENED"]
 *
 * // 2. Mutable State Store Engine (for configurations, registries & read projections)
 * StateStoreEngine<String, Double> stateEngine = PersistenceEnginesJdk.stateStore();
 * stateEngine.save("tap-01", 0.064);
 *
 * Optional<Double> flowRate = stateEngine.findById("tap-01"); // Optional.of(0.064)
 * boolean exists = stateEngine.exists("tap-01");              // true
 * }</pre>
 */
public sealed interface PersistenceEnginesJdk permits PersistenceEnginesJdk.Closed {

    /**
     * Private uninstantiable marker preventing external implementation of this facade interface.
     */
    final class Closed implements PersistenceEnginesJdk {
        private Closed() {}
    }

    /**
     * Creates a new, isolated, thread-safe append-only {@link EventStreamEngine}.
     *
     * @param <S>    the stream partition identifier type (e.g., {@code String}, {@code UUID})
     * @param <E_ID> the globally unique event identifier type (e.g., {@code UUID})
     * @param <E>    the immutable event payload type
     * @return a fresh pure-JDK {@link EventStreamEngine} instance
     */
    static <S, E_ID, E> EventStreamEngine<S, E_ID, E> eventStream() {
        return new InMemoryEventStream<>();
    }

    /**
     * Creates a new, isolated, thread-safe mutable {@link StateStoreEngine}.
     *
     * @param <ID> the unique entity identifier type (e.g., {@code String}, {@code UUID})
     * @param <T>  the entity or state snapshot type
     * @return a fresh pure-JDK {@link StateStoreEngine} instance
     */
    static <ID, T> StateStoreEngine<ID, T> stateStore() {
        return new InMemoryStateStore<>();
    }
}
