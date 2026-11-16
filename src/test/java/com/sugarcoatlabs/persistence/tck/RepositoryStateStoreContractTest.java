package com.sugarcoatlabs.persistence.tck;

import com.sugarcoatlabs.persistence.exception.DuplicateEntityException;
import com.sugarcoatlabs.persistence.exception.EntityNotFoundException;
import com.sugarcoatlabs.persistence.state.RepositoryStateStore;
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
 * Abstract Behavioral &amp; Concurrency Contract for {@link RepositoryStateStore}.
 *
 * <p>Verifies Spring Data repository semantics ({@code insert}, {@code update},
 * {@code save}, {@code findById}, {@code findAll}, {@code existsById}, and
 * {@code deleteById}) as well as fail-fast validation and concurrent uniqueness.</p>
 *
 * @param <T> the {@link RepositoryStateStore} implementation under test
 */
public abstract class RepositoryStateStoreContractTest<
        T extends RepositoryStateStore<String, StateStoreEngineContractTest.DispenserConfig>> {

    private T repository;

    /** Supplies the concrete {@link RepositoryStateStore} under test. */
    protected abstract T getRepository();

    /** Resets the underlying storage state before each test. */
    protected abstract void resetStore();

    @BeforeEach
    void setUp() {
        resetStore();
        this.repository = getRepository();
    }

    @Test
    @DisplayName("Should reject null delegate in RepositoryStateStore.adapting()")
    void shouldRejectNullDelegate() {
        assertThatThrownBy(() -> RepositoryStateStore.adapting(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should insert, update, save, retrieve, check existence, and delete entities")
    void shouldPerformRepositoryLifecycle() {
        var initial = new StateStoreEngineContractTest.DispenserConfig("tap-1", 0.064);
        assertThat(repository.existsById("tap-1")).isFalse();
        assertThat(repository.findById("tap-1")).isEmpty();

        // 1. insert
        repository.insert("tap-1", initial);
        assertThat(repository.existsById("tap-1")).isTrue();
        assertThat(repository.findById("tap-1")).contains(initial);

        // 2. update
        var updated = new StateStoreEngineContractTest.DispenserConfig("tap-1", 0.128);
        repository.update("tap-1", updated);
        assertThat(repository.findById("tap-1")).contains(updated);

        // 3. save (upsert existing and new)
        var savedExisting = new StateStoreEngineContractTest.DispenserConfig("tap-1", 0.256);
        repository.save("tap-1", savedExisting);
        assertThat(repository.findById("tap-1")).contains(savedExisting);

        var savedNew = new StateStoreEngineContractTest.DispenserConfig("tap-2", 0.512);
        repository.save("tap-2", savedNew);
        assertThat(repository.findById("tap-2")).contains(savedNew);

        // 4. findAll unmodifiable snapshot
        List<StateStoreEngineContractTest.DispenserConfig> all = repository.findAll();
        assertThat(all)
                .containsExactlyInAnyOrder(savedExisting, savedNew)
                .isUnmodifiable();

        // 5. deleteById
        assertThat(repository.deleteById("tap-1")).isTrue();
        assertThat(repository.deleteById("tap-1")).isFalse();
        assertThat(repository.existsById("tap-1")).isFalse();
    }

    @Test
    @DisplayName("Should throw DuplicateEntityException on duplicate insert")
    void shouldRejectDuplicateOnInsert() {
        var original = new StateStoreEngineContractTest.DispenserConfig("tap-1", 0.064);
        repository.insert("tap-1", original);

        assertThatThrownBy(() -> repository.insert("tap-1", new StateStoreEngineContractTest.DispenserConfig("tap-1", 0.999)))
                .isInstanceOf(DuplicateEntityException.class)
                .extracting(ex -> ((DuplicateEntityException) ex).id())
                .isEqualTo("tap-1");

        assertThat(repository.findById("tap-1")).contains(original);
    }

    @Test
    @DisplayName("Should throw EntityNotFoundException when updating a missing entity")
    void shouldRejectMissingKeyOnUpdate() {
        assertThatThrownBy(() -> repository.update("missing", new StateStoreEngineContractTest.DispenserConfig("missing", 0.1)))
                .isInstanceOf(EntityNotFoundException.class)
                .extracting(ex -> ((EntityNotFoundException) ex).id())
                .isEqualTo("missing");

        assertThat(repository.existsById("missing")).isFalse();
    }

    @Test
    @DisplayName("Should reject null or blank keys and null entities across all operations")
    void shouldValidateArguments() {
        var valid = new StateStoreEngineContractTest.DispenserConfig("tap-1", 0.064);

        assertThatThrownBy(() -> repository.insert(null, valid)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.insert(" ", valid)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.insert("tap-1", null)).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> repository.update(null, valid)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.update(" ", valid)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.update("tap-1", null)).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> repository.save(null, valid)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.save(" ", valid)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.save("tap-1", null)).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> repository.findById(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.existsById(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.deleteById(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should allow strictly one winner when 100 threads race on insert")
    void concurrentInsert_allowsExactlyOneWinner() throws InterruptedException {
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
                        repository.insert(contestedKey, new StateStoreEngineContractTest.DispenserConfig(contestedKey, rate));
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
