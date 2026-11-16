package com.sugarcoatlabs.persistence;

import com.sugarcoatlabs.persistence.event.EventStreamEngine;
import com.sugarcoatlabs.persistence.lifecycle.ResettableEngine;
import com.sugarcoatlabs.persistence.state.StateStoreEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verification suite for the {@link PersistenceEngines} entry point and
 * {@link Jdk#inMemory()} pipeline.
 *
 * <h2>Behavior Verified</h2>
 * <ul>
 *   <li>{@link #shouldRejectNullPipeline()} &ndash; Confirms
 *       {@link PersistenceEngines#using(PersistencePipeline)} fails fast with
 *       {@link IllegalArgumentException} when passed {@code null}.</li>
 *   <li>{@link #shouldCreateIsolatedEventStreamEngines()} &ndash; Confirms each pipeline
 *       invocation returns a distinct, non-null {@link EventStreamEngine} with isolated
 *       partition state.</li>
 *   <li>{@link #shouldCreateIsolatedStateStoreEngines()} &ndash; Confirms each pipeline
 *       invocation returns a distinct, non-null {@link StateStoreEngine} with isolated
 *       key-value state.</li>
 *   <li>{@link #shouldNotExposeResettableEngineAsCompileTimeContract()} &ndash; Verifies
 *       that terminal pipeline return types adhere strictly to ISP without exposing
 *       {@link ResettableEngine} in method signatures.</li>
 * </ul>
 */
class PersistenceEnginesTest {

    /**
     * Ensures {@link PersistenceEngines#using(PersistencePipeline)} fails fast when
     * provided a {@code null} pipeline.
     */
    @Test
    @DisplayName("using(null) should throw IllegalArgumentException")
    void shouldRejectNullPipeline() {
        assertThatThrownBy(() -> PersistenceEngines.using(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null");
    }

    /**
     * Ensures {@link Jdk.InMemoryPipeline#eventStream()} creates independent engine
     * instances that share no stream partitions or sequence counters.
     */
    @Test
    @DisplayName("using(Jdk.inMemory()).eventStream() should return fresh, isolated EventStreamEngine instances")
    void shouldCreateIsolatedEventStreamEngines() {
        EventStreamEngine<String, UUID, String> engine1 =
                PersistenceEngines.using(Jdk.inMemory()).eventStream();
        EventStreamEngine<String, UUID, String> engine2 =
                PersistenceEngines.using(Jdk.inMemory()).eventStream();

        assertThat(engine1).isNotNull().isNotSameAs(engine2);

        engine1.append("stream-1", UUID.randomUUID(), 1L, 1L, "EVENT_A");
        assertThat(engine1.currentSequence("stream-1")).isEqualTo(1L);
        assertThat(engine2.currentSequence("stream-1")).isZero();
    }

    /**
     * Ensures {@link Jdk.InMemoryPipeline#stateStore()} creates independent store
     * instances that share no stored entries.
     */
    @Test
    @DisplayName("using(Jdk.inMemory()).stateStore() should return fresh, isolated StateStoreEngine instances")
    void shouldCreateIsolatedStateStoreEngines() {
        StateStoreEngine<String, String> store1 =
                PersistenceEngines.using(Jdk.inMemory()).stateStore();
        StateStoreEngine<String, String> store2 =
                PersistenceEngines.using(Jdk.inMemory()).stateStore();

        assertThat(store1).isNotNull().isNotSameAs(store2);

        store1.putIfAbsent("key-1", "VALUE_A");
        assertThat(store1.exists("key-1")).isTrue();
        assertThat(store2.exists("key-1")).isFalse();
    }


    /**
     * Verifies via reflection that {@link Jdk.InMemoryPipeline} terminal methods
     * expose only segregated production interfaces and never {@link ResettableEngine}.
     */
    @Test
    @DisplayName("Pipeline factory methods must not expose ResettableEngine in return types")
    void shouldNotExposeResettableEngineAsCompileTimeContract() throws NoSuchMethodException {
        Class<?> eventReturn = Jdk.InMemoryPipeline.class
                .getMethod("eventStream")
                .getReturnType();
        Class<?> stateReturn = Jdk.InMemoryPipeline.class
                .getMethod("stateStore")
                .getReturnType();

        assertThat(eventReturn).isEqualTo(EventStreamEngine.class);
        assertThat(stateReturn).isEqualTo(StateStoreEngine.class);
        assertThat(ResettableEngine.class.isAssignableFrom(eventReturn)).isFalse();
        assertThat(ResettableEngine.class.isAssignableFrom(stateReturn)).isFalse();
    }

    /**
     * Ensures reusing a single {@link Jdk.InMemoryPipeline} instance across multiple
     * factory calls still produces fresh, isolated engine instances.
     */
    @Test
    @DisplayName("Reusing a single Jdk.inMemory() pipeline should still produce isolated engines")
    void shouldCreateIsolatedEnginesWhenReusingPipelineInstance() {
        var pipeline = Jdk.inMemory();

        var store1 = PersistenceEngines.using(pipeline).stateStore();
        var store2 = PersistenceEngines.using(pipeline).stateStore();
        store1.putIfAbsent("key-1", "VALUE_A");

        assertThat(store1).isNotSameAs(store2);
        assertThat(store2.exists("key-1")).isFalse();

        var stream1 = PersistenceEngines.using(pipeline).eventStream();
        var stream2 = PersistenceEngines.using(pipeline).eventStream();
        stream1.append("stream-1", UUID.randomUUID(), 1L, 1L, "EVENT_A");

        assertThat(stream1).isNotSameAs(stream2);
        assertThat(stream2.currentSequence("stream-1")).isZero();
    }

}
