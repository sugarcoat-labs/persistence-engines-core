package com.sugarcoatlabs.persistence.state;

import com.sugarcoatlabs.persistence.lifecycle.ResettableEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Abstract Behavioral &amp; Concurrency Contract for {@link StateStoreEngine}
 *
 * <p>Defines the authoritative specification that any {@link StateStoreEngine}
 * implementation must satisfy. Subclasses supply a concrete store instance via
 * {@link #getStore()} and inherit this suite to verify strict adherence to
 * <strong>thread safety</strong>, CRUD semantics, defensive snapshot boundaries,
 * and fail-fast argument validation guarantees.</p>
 *
 * <p>Implementations are verified to remain fully thread-safe under high contention—
 * ensuring concurrent readers and writers never corrupt internal state, observe partial
 * entries, or trigger {@link java.util.ConcurrentModificationException}—while applying
 * atomic, non-blocking last-write-wins updates without pessimistic client-held locks.</p>
 *
 * <h3>Operations</h3>
 * <ul>
 *   <li>{@link #shouldPerformCrudLifecycle()} &ndash; Verifies create, read, overwrite,
 *       existence checks, and idempotent deletion semantics.</li>
 *   <li>{@link #shouldReturnUnmodifiableSnapshot()} &ndash; Confirms
 *       {@link StateStoreEngine#findAll()} returns an unmodifiable point-in-time
 *       snapshot.</li>
 *   <li>{@link #shouldValidateArguments()} &ndash; Verifies fail-fast argument checks
 *       on {@code null} or blank keys and {@code null} entities.</li>
 * </ul>
 *
 * <h3>Lifecycle</h3>
 * <ul>
 *   <li>{@link #shouldClearAllEntries()} &ndash; Confirms {@link ResettableEngine#clear()}
 *       wipes all stored entries between tests.</li>
 * </ul>
 *
 * <h3>Concurrency</h3>
 * <ul>
 *   <li>{@link #concurrentReadsAndWrites_remainConsistent()} &ndash; Contention stress
 *       test running 1,000 virtual threads reading and writing concurrently without
 *       state corruption.</li>
 * </ul>
 *
 * @param <T> the resettable {@link StateStoreEngine} implementation under test
 * @see StateStoreEngine
 * @see ResettableEngine
 */
public abstract class StateStoreEngineContractTest<
        T extends StateStoreEngine<String, StateStoreEngineContractTest.DispenserConfig>
                & ResettableEngine> {

    public record DispenserConfig(String id, double flowRate) {}

    private T store;

    /**
     * Supplies the concrete resettable {@link StateStoreEngine} under test.
     *
     * @return a non-null store implementing {@link StateStoreEngine} and
     *         {@link ResettableEngine}
     */
    protected abstract T getStore();

    /**
     * Initializes the store under test and wipes residual entries before each test.
     */
    @BeforeEach
    void setUp() {
        this.store = getStore();
        this.store.clear();
    }

    /**
     * Verifies full CRUD lifecycle semantics: absence checks, initial save, retrieval,
     * overwrite updates, and idempotent deletion.
     */
    @Test
    @DisplayName("Should save, retrieve, update, and check existence of state snapshots")
    void shouldPerformCrudLifecycle() {
        var initial = new DispenserConfig("tap-1", 0.064);
        assertThat(store.exists("tap-1")).isFalse();
        assertThat(store.findById("tap-1")).isEmpty();

        store.save("tap-1", initial);
        assertThat(store.exists("tap-1")).isTrue();
        assertThat(store.findById("tap-1")).contains(initial);

        var updated = new DispenserConfig("tap-1", 0.128);
        store.save("tap-1", updated);
        assertThat(store.findById("tap-1")).contains(updated);

        assertThat(store.deleteById("tap-1")).isTrue();
        assertThat(store.deleteById("tap-1")).isFalse();
        assertThat(store.exists("tap-1")).isFalse();
    }

    /**
     * Ensures {@link StateStoreEngine#findAll()} returns an unmodifiable snapshot
     * containing all currently persisted entries.
     */
    @Test
    @DisplayName("Should return unmodifiable list from findAll()")
    void shouldReturnUnmodifiableSnapshot() {
        var c1 = new DispenserConfig("tap-1", 0.064);
        var c2 = new DispenserConfig("tap-2", 0.128);
        store.save(c1.id(), c1);
        store.save(c2.id(), c2);

        List<DispenserConfig> all = store.findAll();
        assertThat(all)
                .containsExactlyInAnyOrder(c1, c2)
                .isUnmodifiable();
    }

    /**
     * Verifies fail-fast {@link IllegalArgumentException} validation across all
     * mutating and querying operations for {@code null} or blank keys and {@code null}
     * entities.
     */
    @Test
    @DisplayName("Should reject null or blank keys and null entities")
    void shouldValidateArguments() {
        var valid = new DispenserConfig("tap-1", 0.064);
        assertThatThrownBy(() -> store.save(null, valid))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.save("  ", valid))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.save("tap-1", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.findById(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.exists(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.deleteById(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Ensures {@link ResettableEngine#clear()} removes all stored entries and resets
     * existence lookups.
     */
    @Test
    @DisplayName("Should clear all entries")
    void shouldClearAllEntries() {
        store.save("tap-1", new DispenserConfig("tap-1", 0.064));
        store.clear();

        assertThat(store.findAll()).isEmpty();
        assertThat(store.exists("tap-1")).isFalse();
    }

    /**
     * Stress-tests concurrent reads, writes, existence checks, and full snapshots
     * across 1,000 virtual threads contending on 50 shared keys.
     *
     * <p>Verifies that concurrent mutations and {@link StateStoreEngine#findAll()}
     * snapshots never throw {@link java.util.ConcurrentModificationException} or leave
     * corrupted key mappings.</p>
     *
     * <p><b>Executor Lifecycle Note:</b> Uses explicit {@code shutdownNow()} in
     * {@code finally} rather than {@code try-with-resources} ({@link ExecutorService#close()})
     * because {@code close()} waits up to 24 hours for uncompleted tasks if an assertion
     * fails early. Explicit {@code shutdownNow()} interrupts workers immediately.</p>
     *
     * @throws InterruptedException if virtual thread coordination is interrupted
     */
    @Test
    @SuppressWarnings("resource")
    @DisplayName("Concurrency: 1,000 virtual threads reading and writing without corruption")
    void concurrentReadsAndWrites_remainConsistent() throws InterruptedException {
        int workers = 1_000;
        AtomicReference<Throwable> unexpectedError = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(workers);

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            for (int i = 0; i < workers; i++) {
                final int idx = i;
                executor.submit(() -> {
                    try {
                        String id = "tap-" + (idx % 50);
                        store.save(id, new DispenserConfig(id, idx * 0.001));
                        store.findById(id);
                        store.exists(id);
                        store.findAll();
                    } catch (Throwable t) {
                        unexpectedError.compareAndSet(null, t);
                    } finally {
                        done.countDown();
                    }
                });
            }
            assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        assertThat(unexpectedError.get()).isNull();
        assertThat(store.findAll()).hasSize(50);
    }
}
