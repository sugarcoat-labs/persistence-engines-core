package com.sugarcoatlabs.persistence.tck;

import com.sugarcoatlabs.persistence.exception.DuplicateEntityException;
import com.sugarcoatlabs.persistence.exception.EntityNotFoundException;
import com.sugarcoatlabs.persistence.state.EntityStateStore;
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
 * Abstract Behavioral &amp; Concurrency Contract for {@link EntityStateStore}.
 *
 * <p>Verifies Jakarta Persistence ({@code EntityManager}) semantics ({@code persist},
 * {@code replace}, {@code merge}, {@code find}, {@code findAll}, {@code contains}, and
 * {@code remove}) as well as fail-fast validation and concurrent uniqueness.</p>
 *
 * @param <T> the {@link EntityStateStore} implementation under test
 */
public abstract class EntityStateStoreContractTest<
        T extends EntityStateStore<String, StateStoreEngineContractTest.DispenserConfig>> {

    private T entityStore;

    /** Supplies the concrete {@link EntityStateStore} under test. */
    protected abstract T getEntityStore();

    /** Resets the underlying storage state before each test. */
    protected abstract void resetStore();

    @BeforeEach
    void setUp() {
        resetStore();
        this.entityStore = getEntityStore();
    }

    @Test
    @DisplayName("Should reject null delegate in EntityStateStore.adapting()")
    void shouldRejectNullDelegate() {
        assertThatThrownBy(() -> EntityStateStore.adapting(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should persist, replace, merge, find, check contains, and remove entities")
    void shouldPerformEntityManagerLifecycle() {
        var initial = new StateStoreEngineContractTest.DispenserConfig("tap-1", 0.064);
        assertThat(entityStore.contains("tap-1")).isFalse();
        assertThat(entityStore.find("tap-1")).isEmpty();

        // 1. persist
        entityStore.persist("tap-1", initial);
        assertThat(entityStore.contains("tap-1")).isTrue();
        assertThat(entityStore.find("tap-1")).contains(initial);

        // 2. replace
        var replaced = new StateStoreEngineContractTest.DispenserConfig("tap-1", 0.128);
        entityStore.replace("tap-1", replaced);
        assertThat(entityStore.find("tap-1")).contains(replaced);

        // 3. merge (upsert existing and new)
        var mergedExisting = new StateStoreEngineContractTest.DispenserConfig("tap-1", 0.256);
        entityStore.merge("tap-1", mergedExisting);
        assertThat(entityStore.find("tap-1")).contains(mergedExisting);

        var mergedNew = new StateStoreEngineContractTest.DispenserConfig("tap-2", 0.512);
        entityStore.merge("tap-2", mergedNew);
        assertThat(entityStore.find("tap-2")).contains(mergedNew);

        // 4. findAll unmodifiable snapshot
        List<StateStoreEngineContractTest.DispenserConfig> all = entityStore.findAll();
        assertThat(all)
                .containsExactlyInAnyOrder(mergedExisting, mergedNew)
                .isUnmodifiable();

        // 5. remove
        assertThat(entityStore.remove("tap-1")).isTrue();
        assertThat(entityStore.remove("tap-1")).isFalse();
        assertThat(entityStore.contains("tap-1")).isFalse();
    }

    @Test
    @DisplayName("Should throw DuplicateEntityException on duplicate persist")
    void shouldRejectDuplicateOnPersist() {
        var original = new StateStoreEngineContractTest.DispenserConfig("tap-1", 0.064);
        entityStore.persist("tap-1", original);

        assertThatThrownBy(() -> entityStore.persist("tap-1", new StateStoreEngineContractTest.DispenserConfig("tap-1", 0.999)))
                .isInstanceOf(DuplicateEntityException.class)
                .extracting(ex -> ((DuplicateEntityException) ex).id())
                .isEqualTo("tap-1");

        assertThat(entityStore.find("tap-1")).contains(original);
    }

    @Test
    @DisplayName("Should throw EntityNotFoundException when replacing a missing entity")
    void shouldRejectMissingKeyOnReplace() {
        assertThatThrownBy(() -> entityStore.replace("missing", new StateStoreEngineContractTest.DispenserConfig("missing", 0.1)))
                .isInstanceOf(EntityNotFoundException.class)
                .extracting(ex -> ((EntityNotFoundException) ex).id())
                .isEqualTo("missing");

        assertThat(entityStore.contains("missing")).isFalse();
    }

    @Test
    @DisplayName("Should reject null or blank keys and null entities across all operations")
    void shouldValidateArguments() {
        var valid = new StateStoreEngineContractTest.DispenserConfig("tap-1", 0.064);

        assertThatThrownBy(() -> entityStore.persist(null, valid)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> entityStore.persist(" ", valid)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> entityStore.persist("tap-1", null)).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> entityStore.replace(null, valid)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> entityStore.replace(" ", valid)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> entityStore.replace("tap-1", null)).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> entityStore.merge(null, valid)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> entityStore.merge(" ", valid)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> entityStore.merge("tap-1", null)).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> entityStore.find(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> entityStore.contains(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> entityStore.remove(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should allow strictly one winner when 100 threads race on persist")
    void concurrentPersist_allowsExactlyOneWinner() throws InterruptedException {
        int threads = 100;
        String contestedKey = "contested-tap";
        var ready = new CountDownLatch(threads);
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(threads);
        var successes = new AtomicInteger(0);
        var duplicates = new AtomicInteger(0);
        var failure = new AtomicReference<Throwable>();

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            for (int i = 0; i < threads; i++) {
                final double rate = 0.01 * (i + 1);
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        entityStore.persist(contestedKey, new StateStoreEngineContractTest.DispenserConfig(contestedKey, rate));
                        successes.incrementAndGet();
                    } catch (DuplicateEntityException expected) {
                        duplicates.incrementAndGet();
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
        assertThat(successes.get()).isEqualTo(1);
        assertThat(duplicates.get()).isEqualTo(threads - 1);
    }
}
