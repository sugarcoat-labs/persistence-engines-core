package com.sugarcoatlabs.persistence.tck;

import com.sugarcoatlabs.persistence.exception.DuplicateEntityException;
import com.sugarcoatlabs.persistence.exception.EntityNotFoundException;
import com.sugarcoatlabs.persistence.state.StateStoreEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Abstract Behavioral &amp; Concurrency Contract for {@link StateStoreEngine}
 *
 * <p>Defines the authoritative specification that any {@link StateStoreEngine}
 * implementation must satisfy. Subclasses supply a concrete store instance via
 * {@link #getStore()} and inherit this suite to verify strict adherence to
 * <strong>thread safety</strong>, explicit JSR-107 write semantics ({@code putIfAbsent},
 * {@code replace}, {@code put}), defensive snapshot boundaries, and fail-fast argument
 * validation guarantees.</p>
 *
 * <p>Implementations are verified to remain fully thread-safe under high contention—
 * ensuring concurrent readers and writers never corrupt internal state, observe partial
 * entries, or trigger {@link java.util.ConcurrentModificationException}—while enforcing
 * atomic conditional inserts and updates without pessimistic client-held locks.</p>
 *
 * <h3>Operations</h3>
 * <ul>
 *   <li>{@link #shouldPerformCrudLifecycle()} &ndash; Verifies {@code putIfAbsent},
 *       {@code replace}, {@code put}, retrieval, existence checks, and idempotent deletion.</li>
 *   <li>{@link #shouldRejectDuplicateOnPutIfAbsent()} &ndash; Verifies {@code putIfAbsent}
 *       throws {@link DuplicateEntityException} with the conflicting ID without overwriting.</li>
 *   <li>{@link #shouldRejectMissingKeyOnReplace()} &ndash; Verifies {@code replace} throws
 *       {@link EntityNotFoundException} with the missing ID without inserting a ghost entry.</li>
 *   <li>{@link #shouldAllowReplaceWithIdenticalPayload()} &ndash; Verifies {@code replace}
 *       succeeds when the key exists even if the replacement payload is equal to the current value.</li>
 *   <li>{@link #shouldReturnUnmodifiableSnapshot()} &ndash; Confirms
 *       {@link StateStoreEngine#findAll()} returns an unmodifiable point-in-time snapshot.</li>
 *   <li>{@link #shouldValidateArguments()} &ndash; Verifies fail-fast argument checks
 *       on {@code null} or blank keys and {@code null} entities across all methods.</li>
 * </ul>
 *
 * <h3>Lifecycle</h3>
 * <ul>
 *   <li>{@link #shouldClearAllEntries()} &ndash; Confirms {@link #resetStore()}
 *       wipes all stored entries between tests.</li>
 * </ul>
 *
 * <h3>Concurrency</h3>
 * <ul>
 *   <li>{@link #concurrentPutIfAbsent_allowsExactlyOneWinner()} &ndash; Verifies atomic
 *       uniqueness under 200 virtual threads racing to insert the exact same key.</li>
 *   <li>{@link #concurrentReadsAndWrites_remainConsistent()} &ndash; Contention stress
 *       test running 1,000 virtual threads reading and writing concurrently without
 *       state corruption.</li>
 * </ul>
 *
 * @param <T> the {@link StateStoreEngine} implementation under test
 * @see StateStoreEngine
 */
public abstract class StateStoreEngineContractTest<
        T extends StateStoreEngine<String, StateStoreEngineContractTest.DispenserConfig>> {

    public record DispenserConfig(String id, double flowRate) {}

    private T store;

    /**
     * Supplies the concrete {@link StateStoreEngine} under test.
     *
     * @return a non-null store implementing {@link StateStoreEngine}
     */
    protected abstract T getStore();

    /**
     * Resets the underlying storage state before each test and during lifecycle tests.
     */
    protected abstract void resetStore();

    /**
     * Initializes the store under test and wipes residual entries before each test.
     */
    @BeforeEach
    void setUp() {
        resetStore();
        this.store = getStore();
    }

    /**
     * Verifies full CRUD lifecycle semantics across {@code putIfAbsent}, {@code replace},
     * {@code put}, retrieval, existence checks, and idempotent deletion.
     */
    @Test
    @DisplayName("Should insert, replace, upsert, retrieve, and delete state snapshots")
    void shouldPerformCrudLifecycle() {
        var initial = new DispenserConfig("tap-1", 0.064);
        assertThat(store.exists("tap-1")).isFalse();
        assertThat(store.findById("tap-1")).isEmpty();

        // 1. Insert via putIfAbsent
        store.putIfAbsent("tap-1", initial);
        assertThat(store.exists("tap-1")).isTrue();
        assertThat(store.findById("tap-1")).contains(initial);

        // 2. Update existing entry via replace
        var replaced = new DispenserConfig("tap-1", 0.128);
        store.replace("tap-1", replaced);
        assertThat(store.findById("tap-1")).contains(replaced);

        // 3. Upsert existing and new entries via put
        var upsertedExisting = new DispenserConfig("tap-1", 0.256);
        store.put("tap-1", upsertedExisting);
        assertThat(store.findById("tap-1")).contains(upsertedExisting);

        var upsertedNew = new DispenserConfig("tap-2", 0.512);
        store.put("tap-2", upsertedNew);
        assertThat(store.findById("tap-2")).contains(upsertedNew);

        // 4. Delete and verify idempotency
        assertThat(store.deleteById("tap-1")).isTrue();
        assertThat(store.deleteById("tap-1")).isFalse();
        assertThat(store.exists("tap-1")).isFalse();
        assertThat(store.exists("tap-2")).isTrue();
    }

    /**
     * Ensures {@link StateStoreEngine#putIfAbsent} fails with {@link DuplicateEntityException}
     * and preserves the original stored snapshot when the key already exists.
     */
    @Test
    @DisplayName("Should reject duplicate key on putIfAbsent without overwriting existing value")
    void shouldRejectDuplicateOnPutIfAbsent() {
        var original = new DispenserConfig("tap-1", 0.064);
        var duplicateAttempt = new DispenserConfig("tap-1", 0.999);

        store.putIfAbsent("tap-1", original);

        assertThatThrownBy(() -> store.putIfAbsent("tap-1", duplicateAttempt))
                .isInstanceOf(DuplicateEntityException.class)
                .extracting(ex -> ((DuplicateEntityException) ex).id())
                .isEqualTo("tap-1");

        assertThat(store.findById("tap-1")).contains(original);
    }

    /**
     * Ensures {@link StateStoreEngine#replace} fails with {@link EntityNotFoundException}
     * and does not create a new entry when the key does not exist.
     */
    @Test
    @DisplayName("Should reject missing key on replace without creating an entry")
    void shouldRejectMissingKeyOnReplace() {
        var updateAttempt = new DispenserConfig("tap-missing", 0.128);

        assertThatThrownBy(() -> store.replace("tap-missing", updateAttempt))
                .isInstanceOf(EntityNotFoundException.class)
                .extracting(ex -> ((EntityNotFoundException) ex).id())
                .isEqualTo("tap-missing");

        assertThat(store.exists("tap-missing")).isFalse();
        assertThat(store.findById("tap-missing")).isEmpty();
    }

    /**
     * Ensures {@link StateStoreEngine#replace} succeeds when the key exists even if
     * the replacement entity is equal to the currently stored entity (guarding against
     * SQL affected-rows vs. matched-rows discrepancies).
     */
    @Test
    @DisplayName("Should succeed when replacing an existing key with an identical payload")
    void shouldAllowReplaceWithIdenticalPayload() {
        var config = new DispenserConfig("tap-1", 0.064);
        store.putIfAbsent("tap-1", config);

        store.replace("tap-1", new DispenserConfig("tap-1", 0.064));

        assertThat(store.findById("tap-1")).contains(config);
    }

    /**
     * Ensures distinct entity identifiers with identical payloads are stored as separate
     * independent entries without colliding or overwriting each other.
     */
    @Test
    @DisplayName("Should store distinct keys with identical payloads as independent entries")
    void shouldStoreDistinctKeysWithIdenticalPayloadsIndependently() {
        var tap1 = new DispenserConfig("tap-1", 0.064);
        var tap2 = new DispenserConfig("tap-2", 0.064);

        store.putIfAbsent("tap-1", tap1);
        store.putIfAbsent("tap-2", tap2);

        assertThat(store.exists("tap-1")).isTrue();
        assertThat(store.exists("tap-2")).isTrue();
        assertThat(store.findById("tap-1")).contains(tap1);
        assertThat(store.findById("tap-2")).contains(tap2);
        assertThat(store.findAll()).containsExactlyInAnyOrder(tap1, tap2);

        assertThat(store.deleteById("tap-1")).isTrue();
        assertThat(store.exists("tap-1")).isFalse();
        assertThat(store.exists("tap-2")).isTrue();
        assertThat(store.findAll()).containsExactly(tap2);
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
        store.putIfAbsent(c1.id(), c1);
        store.putIfAbsent(c2.id(), c2);

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
    @DisplayName("Should reject null or blank keys and null entities across all operations")
    void shouldValidateArguments() {
        var valid = new DispenserConfig("tap-1", 0.064);

        // putIfAbsent validation
        assertThatThrownBy(() -> store.putIfAbsent(null, valid))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.putIfAbsent("  ", valid))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.putIfAbsent("tap-1", null))
                .isInstanceOf(IllegalArgumentException.class);

        // replace validation
        assertThatThrownBy(() -> store.replace(null, valid))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.replace("  ", valid))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.replace("tap-1", null))
                .isInstanceOf(IllegalArgumentException.class);

        // put validation
        assertThatThrownBy(() -> store.put(null, valid))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.put("  ", valid))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.put("tap-1", null))
                .isInstanceOf(IllegalArgumentException.class);

        // Query & delete validation
        assertThatThrownBy(() -> store.findById(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.findById("  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.exists(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.exists(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.deleteById(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.deleteById(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Ensures {@link #resetStore()} removes all stored entries and resets
     * existence lookups.
     */
    @Test
    @DisplayName("Should clear all entries")
    void shouldClearAllEntries() {
        store.putIfAbsent("tap-1", new DispenserConfig("tap-1", 0.064));
        resetStore();
        this.store = getStore();

        assertThat(store.findAll()).isEmpty();
        assertThat(store.exists("tap-1")).isFalse();
    }

    /**
     * Verifies atomic uniqueness when 200 virtual threads simultaneously invoke
     * {@link StateStoreEngine#putIfAbsent} for the exact same identifier.
     *
     * <p>Strictly one thread must succeed and persist its value while all remaining
     * 199 threads must fail with {@link DuplicateEntityException}.</p>
     */
    @Test
    @DisplayName("Should allow strictly one winner when 200 threads race on putIfAbsent")
    void concurrentPutIfAbsent_allowsExactlyOneWinner() throws InterruptedException {
        int threads = 200;
        String contestedKey = "contested-tap";
        var ready = new CountDownLatch(threads);
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(threads);
        var successes = new AtomicInteger(0);
        var duplicates = new AtomicInteger(0);
        var winningValue = new AtomicReference<DispenserConfig>();
        var unexpectedFailure = new AtomicReference<Throwable>();

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            for (int i = 0; i < threads; i++) {
                final double flowRate = 0.01 * (i + 1);
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        var candidate = new DispenserConfig(contestedKey, flowRate);
                        store.putIfAbsent(contestedKey, candidate);
                        successes.incrementAndGet();
                        winningValue.set(candidate);
                    } catch (DuplicateEntityException expected) {
                        duplicates.incrementAndGet();
                    } catch (Throwable t) {
                        unexpectedFailure.compareAndSet(null, t);
                    } finally {
                        done.countDown();
                    }
                });
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        assertThat(unexpectedFailure.get()).isNull();
        assertThat(successes.get()).isEqualTo(1);
        assertThat(duplicates.get()).isEqualTo(threads - 1);
        assertThat(store.findById(contestedKey)).contains(winningValue.get());
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
     */
    @Test
    @DisplayName("Should maintain consistency under 1,000 concurrent virtual threads")
    void concurrentReadsAndWrites_remainConsistent() throws InterruptedException {
        int threads = 1_000;
        int keySpace = 50;
        var ready = new CountDownLatch(threads);
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(threads);
        var failure = new AtomicReference<Throwable>();

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            for (int i = 0; i < threads; i++) {
                final int taskId = i;
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        String key = "tap-" + (taskId % keySpace);
                        var config = new DispenserConfig(key, 0.05 + (taskId % 10) * 0.01);

                        store.put(key, config);
                        try {
                            store.replace(key, new DispenserConfig(key, config.flowRate() + 0.001));
                        } catch (EntityNotFoundException ignored) {
                            // Acceptable if another thread concurrently deleted the key
                        }
                        store.exists(key);
                        store.findById(key).ifPresent(found ->
                                assertThat(found.id()).isEqualTo(key));

                        if (taskId % 20 == 0) {
                            List<DispenserConfig> snapshot = store.findAll();
                            assertThat(snapshot).isNotNull();
                        }
                        if (taskId % 15 == 0) {
                            store.deleteById(key);
                        }
                    } catch (Throwable t) {
                        failure.compareAndSet(null, t);
                    } finally {
                        done.countDown();
                    }
                });
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        assertThat(failure.get()).isNull();
        assertThat(store.findAll().size()).isLessThanOrEqualTo(keySpace);
    }
}
