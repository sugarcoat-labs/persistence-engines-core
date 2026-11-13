package com.sugarcoatlabs.persistence;

import com.sugarcoatlabs.persistence.event.EventStreamEngine;
import com.sugarcoatlabs.persistence.lifecycle.ResettableEngine;
import com.sugarcoatlabs.persistence.state.StateStoreEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verification suite for the {@link PersistenceEnginesJdk} static facade.
 *
 * <h2>Behavior Verified</h2>
 * <ul>
 *   <li>{@link #shouldCreateIsolatedEventStreamEngines()} &ndash; Confirms each factory
 *       call returns a distinct, non-null {@link EventStreamEngine} with isolated
 *       partition state.</li>
 *   <li>{@link #shouldCreateIsolatedStateStoreEngines()} &ndash; Confirms each factory
 *       call returns a distinct, non-null {@link StateStoreEngine} with isolated
 *       key-value state.</li>
 *   <li>{@link #shouldNotExposeResettableEngineAsCompileTimeContract()} &ndash; Verifies
 *       that facade return types adhere strictly to ISP without exposing
 *       {@link ResettableEngine} in method signatures.</li>
 * </ul>
 */
class PersistenceEnginesJdkTest {

    /**
     * Ensures {@link PersistenceEnginesJdk#eventStream()} creates independent engine
     * instances that share no stream partitions or sequence counters.
     */
    @Test
    @DisplayName("eventStream() should return fresh, isolated EventStreamEngine instances")
    void shouldCreateIsolatedEventStreamEngines() {
        EventStreamEngine<String, UUID, String> engine1 = PersistenceEnginesJdk.eventStream();
        EventStreamEngine<String, UUID, String> engine2 = PersistenceEnginesJdk.eventStream();

        assertThat(engine1).isNotNull().isNotSameAs(engine2);

        engine1.append("stream-1", UUID.randomUUID(), 1L, 1L, "EVENT_A");
        assertThat(engine1.currentSequence("stream-1")).isEqualTo(1L);
        assertThat(engine2.currentSequence("stream-1")).isZero();
    }

    /**
     * Ensures {@link PersistenceEnginesJdk#stateStore()} creates independent store
     * instances that share no stored entries.
     */
    @Test
    @DisplayName("stateStore() should return fresh, isolated StateStoreEngine instances")
    void shouldCreateIsolatedStateStoreEngines() {
        StateStoreEngine<String, String> store1 = PersistenceEnginesJdk.stateStore();
        StateStoreEngine<String, String> store2 = PersistenceEnginesJdk.stateStore();

        assertThat(store1).isNotNull().isNotSameAs(store2);

        store1.save("key-1", "VALUE_A");
        assertThat(store1.exists("key-1")).isTrue();
        assertThat(store2.exists("key-1")).isFalse();
    }

    /**
     * Verifies via reflection that {@link PersistenceEnginesJdk} factory return types
     * expose only segregated production interfaces and never {@link ResettableEngine}.
     */
    @Test
    @DisplayName("Facade factory methods must not expose ResettableEngine in return types")
    void shouldNotExposeResettableEngineAsCompileTimeContract() throws NoSuchMethodException {
        Class<?> eventReturn = PersistenceEnginesJdk.class
                .getMethod("eventStream")
                .getReturnType();
        Class<?> stateReturn = PersistenceEnginesJdk.class
                .getMethod("stateStore")
                .getReturnType();

        assertThat(eventReturn).isEqualTo(EventStreamEngine.class);
        assertThat(stateReturn).isEqualTo(StateStoreEngine.class);
        assertThat(ResettableEngine.class.isAssignableFrom(eventReturn)).isFalse();
        assertThat(ResettableEngine.class.isAssignableFrom(stateReturn)).isFalse();
    }
}
