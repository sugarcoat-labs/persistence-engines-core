package com.sugarcoatlabs.persistence.event;

import com.sugarcoatlabs.persistence.lifecycle.ResettableEngine;
import com.sugarcoatlabs.persistence.exception.DuplicateEventException;
import com.sugarcoatlabs.persistence.exception.OptimisticConcurrencyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Abstract Behavioral &amp; Concurrency Contract for {@link EventStreamEngine}
 *
 * <p>Defines the authoritative specification that any {@link EventStreamEngine}
 * implementation must satisfy. Subclasses supply a concrete engine instance via
 * {@link #getEngine()} and inherit this suite to verify strict adherence to
 * <strong>thread safety</strong>, monotonic ordering, idempotency, and
 * <strong>optimistic locking</strong> guarantees.</p>
 *
 * <p>Implementations are verified to remain fully thread-safe under high contention—
 * ensuring concurrent readers and writers never corrupt internal state, observe partial
 * writes, or trigger {@link java.util.ConcurrentModificationException}—while enforcing
 * optimistic concurrency control through expected monotonic sequence numbers rather than
 * pessimistic client-held locks.</p>
 *
 * <h3>Operations</h3>
 * <ul>
 *   <li>{@link #shouldAppendAndFetchInOrder()} &ndash; Verifies 1-indexed sequential
 *       appends and unmodifiable chronological stream snapshots.</li>
 *   <li>{@link #shouldHandleUnknownStreamGracefully()} &ndash; Ensures querying
 *       uninitialized streams returns sequence {@code 0L} and an empty list.</li>
 *   <li>{@link #shouldValidateArguments()} &ndash; Verifies fail-fast argument checks
 *       on {@code null}, blank, or non-positive inputs.</li>
 * </ul>
 *
 * <h3>Lifecycle</h3>
 * <ul>
 *   <li>{@link #shouldClearAllState()} &ndash; Confirms {@link ResettableEngine#clear()}
 *       wipes all streams, sequences, and idempotency state between tests.</li>
 * </ul>
 *
 * <h3>Idempotency</h3>
 * <ul>
 *   <li>{@link #shouldRejectDuplicateEventId()} &ndash; Rejects duplicate {@code eventId}
 *       submissions via {@link DuplicateEventException} without altering history.</li>
 *   <li>{@link #failedSequenceCheckShouldNotPoisonIdempotencySet()} &ndash; Confirms
 *       failed sequence checks execute prior to idempotency registration so retries
 *       succeed.</li>
 *   <li>{@link #concurrentDuplicateEventIds_allowOnlyOneSuccess()} &ndash; Races 200
 *       virtual threads submitting the exact same {@code eventId} to verify atomic
 *       idempotency under contention.</li>
 * </ul>
 *
 * <h3>Concurrency</h3>
 * <ul>
 *   <li>{@link #shouldRejectSequenceMismatch()} &ndash; Validates optimistic locking
 *       rejection via {@link OptimisticConcurrencyException} on stale sequences.</li>
 *   <li>{@link #concurrentAppendsOnSameStream_allowOnlyOneWinner()} &ndash; Contention
 *       stress test racing 500 virtual threads on a single stream version.</li>
 *   <li>{@link #concurrentAppendsAcrossIndependentStreams_succeedCompletely()} &ndash;
 *       Throughput and partition isolation test across 100 parallel streams.</li>
 *   <li>{@link #concurrentReadsDuringWrites_neverThrowOrExposeMutableState()} &ndash;
 *       Snapshot isolation test polling lock-free reads during continuous appends.</li>
 * </ul>
 *
 * @param <T> the resettable {@link EventStreamEngine} implementation under test
 * @see EventStreamEngine
 * @see ResettableEngine
 */
public abstract class EventStreamEngineContractTest<
        T extends EventStreamEngine<String, UUID, String> & ResettableEngine> {

    private T engine;

    /**
     * Supplies the concrete resettable {@link EventStreamEngine} under test.
     *
     * @return a non-null engine implementing {@link EventStreamEngine} and
     *         {@link ResettableEngine}
     */
    protected abstract T getEngine();

    /**
     * Initializes the engine under test and wipes residual state before each test.
     */
    @BeforeEach
    void setUp() {
        this.engine = getEngine();
        this.engine.clear();
    }

    /**
     * Verifies sequential 1-indexed appends and confirms
     * {@link EventStreamEngine#stream(Object)} returns an unmodifiable snapshot.
     */
    @Test
    @DisplayName("Should append events sequentially and return unmodifiable snapshot")
    void shouldAppendAndFetchInOrder() {
        String streamId = "tap-01";
        engine.append(streamId, UUID.randomUUID(), 1L, 1L, "TAP_OPENED");
        engine.append(streamId, UUID.randomUUID(), 2L, 2L, "LIQUID_DISPENSED");
        engine.append(streamId, UUID.randomUUID(), 3L, 3L, "TAP_CLOSED");

        assertThat(engine.currentSequence(streamId)).isEqualTo(3L);

        var history = engine.stream(streamId);
        assertThat(history)
                .containsExactly("TAP_OPENED", "LIQUID_DISPENSED", "TAP_CLOSED")
                .isUnmodifiable();
    }

    /**
     * Ensures querying an unknown stream returns sequence {@code 0L} and an empty list.
     */
    @Test
    @DisplayName("Should return 0 sequence and empty list for unknown stream")
    void shouldHandleUnknownStreamGracefully() {
        assertThat(engine.currentSequence("unknown-stream")).isZero();
        assertThat(engine.stream("unknown-stream")).isEmpty();
    }

    /**
     * Verifies optimistic locking rejects mismatches on either
     * {@code expectedSequence} or {@code eventSequence}.
     */
    @Test
    @DisplayName("Should reject append when sequence mismatches currentSequence + 1")
    void shouldRejectSequenceMismatch() {
        String streamId = "tap-02";
        engine.append(streamId, UUID.randomUUID(), 1L, 1L, "EVENT_1");

        UUID staleExpectedId = UUID.randomUUID();
        assertThatThrownBy(() ->
                engine.append(streamId, staleExpectedId, 2L, 1L, "EVENT_2"))
                .isInstanceOf(OptimisticConcurrencyException.class)
                .satisfies(ex -> {
                    var oce = (OptimisticConcurrencyException) ex;
                    assertThat(oce.streamId()).isEqualTo(streamId);
                    assertThat(oce.expectedSequence()).isEqualTo(1L);
                    assertThat(oce.actualSequence()).isEqualTo(1L);
                });

        UUID gapSequenceId = UUID.randomUUID();
        assertThatThrownBy(() ->
                engine.append(streamId, gapSequenceId, 3L, 2L, "EVENT_3"))
                .isInstanceOf(OptimisticConcurrencyException.class);

        assertThat(engine.currentSequence(streamId)).isEqualTo(1L);
        assertThat(engine.stream(streamId)).containsExactly("EVENT_1");
    }

    /**
     * Verifies duplicate {@code eventId} throws {@link DuplicateEventException}.
     */
    @Test
    @DisplayName("Should reject duplicate eventId and preserve stream integrity")
    void shouldRejectDuplicateEventId() {
        UUID sharedEventId = UUID.randomUUID();
        engine.append("tap-03", sharedEventId, 1L, 1L, "FIRST");

        assertThatThrownBy(() ->
                engine.append("tap-03", sharedEventId, 2L, 2L, "DUPLICATE"))
                .isInstanceOf(DuplicateEventException.class)
                .satisfies(ex -> {
                    var dee = (DuplicateEventException) ex;
                    assertThat(dee.eventId()).isEqualTo(sharedEventId);
                });

        assertThat(engine.currentSequence("tap-03")).isEqualTo(1L);
        assertThat(engine.stream("tap-03")).containsExactly("FIRST");
    }

    /**
     * Ensures failed sequence validation does not pollute the deduplication set.
     */
    @Test
    @DisplayName("Failed sequence validation must NOT pollute idempotency registry")
    void failedSequenceCheckShouldNotPoisonIdempotencySet() {
        UUID eventId = UUID.randomUUID();
        String streamId = "tap-04";

        assertThatThrownBy(() ->
                engine.append(streamId, eventId, 1L, 99L, "PAYLOAD"))
                .isInstanceOf(OptimisticConcurrencyException.class);

        engine.append(streamId, eventId, 1L, 1L, "PAYLOAD");

        assertThat(engine.currentSequence(streamId)).isEqualTo(1L);
        assertThat(engine.stream(streamId)).containsExactly("PAYLOAD");
    }

    /**
     * Verifies fail-fast argument validation across all operations.
     */
    @Test
    @DisplayName("Should reject null, blank, or non-positive sequence arguments")
    void shouldValidateArguments() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> engine.append(null, id, 1L, 1L, "E"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> engine.append("   ", id, 1L, 1L, "E"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> engine.append("s1", null, 1L, 1L, "E"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> engine.append("s1", id, 1L, 1L, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> engine.append("s1", id, 0L, 1L, "E"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> engine.append("s1", id, 1L, 0L, "E"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> engine.currentSequence(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> engine.stream(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Ensures {@link ResettableEngine#clear()} resets all streams and deduplication state.
     */
    @Test
    @DisplayName("Should clear all streams, sequences, and deduplication state")
    void shouldClearAllState() {
        UUID eventId = UUID.randomUUID();
        engine.append("tap-05", eventId, 1L, 1L, "OPEN");

        engine.clear();

        assertThat(engine.currentSequence("tap-05")).isZero();
        assertThat(engine.stream("tap-05")).isEmpty();

        engine.append("tap-05", eventId, 1L, 1L, "OPEN_AGAIN");
        assertThat(engine.currentSequence("tap-05")).isEqualTo(1L);
    }

    /**
     * Stress-tests single-stream optimistic locking under extreme contention.
     *
     * <p>Synchronizes 500 virtual threads behind a starting barrier so all contenders
     * simultaneously attempt to append sequence {@code 1L} to the same stream,
     * asserting exactly one winner and 499 {@link OptimisticConcurrencyException}s.</p>
     *
     * <p><b>Executor Lifecycle Note:</b> Uses explicit {@code shutdownNow()} in
     * {@code finally} rather than {@code try-with-resources} ({@link ExecutorService#close()})
     * because {@code close()} waits up to 24 hours for uncompleted tasks if an assertion
     * fails early. Explicit {@code shutdownNow()} interrupts workers immediately.</p>
     *
     * @throws InterruptedException if virtual thread barrier coordination is interrupted
     */
    @Test
    @SuppressWarnings("resource")
    @DisplayName("Concurrency: 500 virtual threads on same stream allow 1 winner")
    void concurrentAppendsOnSameStream_allowOnlyOneWinner() throws InterruptedException {
        String streamId = "hot-stream";
        int contenders = 500;
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger conflicts = new AtomicInteger(0);
        AtomicReference<Throwable> unexpectedError = new AtomicReference<>();

        CountDownLatch ready = new CountDownLatch(contenders);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(contenders);

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            for (int i = 0; i < contenders; i++) {
                final int workerId = i;
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        engine.append(streamId, UUID.randomUUID(), 1L, 1L,
                                "EVENT-" + workerId);
                        successes.incrementAndGet();
                    } catch (OptimisticConcurrencyException expected) {
                        conflicts.incrementAndGet();
                    } catch (Throwable t) {
                        unexpectedError.compareAndSet(null, t);
                    } finally {
                        done.countDown();
                    }
                });
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        assertThat(unexpectedError.get()).isNull();
        assertThat(successes.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(contenders - 1);
        assertThat(engine.currentSequence(streamId)).isEqualTo(1L);
        assertThat(engine.stream(streamId)).hasSize(1);
    }

    /**
     * Verifies per-stream partition isolation across parallel independent streams.
     *
     * <p>Launches 100 concurrent virtual threads, each appending 20 sequential events to
     * its own stream partition, confirming all 2,000 events persist without interference.</p>
     *
     * <p><b>Executor Lifecycle Note:</b> Uses explicit {@code shutdownNow()} in
     * {@code finally} rather than {@code try-with-resources} so any stalled virtual
     * threads are interrupted immediately on assertion failure.</p>
     *
     * @throws InterruptedException if virtual thread coordination is interrupted
     */
    @Test
    @SuppressWarnings("resource")
    @DisplayName("Concurrency: 100 independent streams complete without interference")
    void concurrentAppendsAcrossIndependentStreams_succeedCompletely()
            throws InterruptedException {
        int streamCount = 100;
        int eventsPerStream = 20;
        AtomicReference<Throwable> unexpectedError = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(streamCount);

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            for (int s = 0; s < streamCount; s++) {
                final String streamId = "stream-" + s;
                executor.submit(() -> {
                    try {
                        for (long seq = 1; seq <= eventsPerStream; seq++) {
                            engine.append(streamId, UUID.randomUUID(), seq, seq,
                                    streamId + "-evt-" + seq);
                        }
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
        for (int s = 0; s < streamCount; s++) {
            String streamId = "stream-" + s;
            assertThat(engine.currentSequence(streamId)).isEqualTo(eventsPerStream);
            assertThat(engine.stream(streamId)).hasSize(eventsPerStream);
        }
    }

    /**
     * Stress-tests atomic idempotency deduplication under duplicate submissions.
     *
     * <p>Races 200 virtual threads submitting the exact same {@code eventId} on the same
     * stream simultaneously, asserting strictly one success and 199 rejections.</p>
     *
     * <p><b>Executor Lifecycle Note:</b> Uses explicit {@code shutdownNow()} in
     * {@code finally} rather than {@code try-with-resources} to prevent
     * {@link ExecutorService#close()} from blocking if a worker stalls after an error.</p>
     *
     * @throws InterruptedException if virtual thread barrier coordination is interrupted
     */
    @Test
    @SuppressWarnings("resource")
    @DisplayName("Concurrency: 200 threads with same eventId allow only 1 success")
    void concurrentDuplicateEventIds_allowOnlyOneSuccess() throws InterruptedException {
        String streamId = "idempotency-race-stream";
        UUID sharedEventId = UUID.randomUUID();
        int contenders = 200;
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger expectedRejections = new AtomicInteger(0);
        AtomicReference<Throwable> unexpectedError = new AtomicReference<>();

        CountDownLatch ready = new CountDownLatch(contenders);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(contenders);

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            for (int i = 0; i < contenders; i++) {
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        engine.append(streamId, sharedEventId, 1L, 1L, "SAME_EVENT");
                        successes.incrementAndGet();
                    } catch (OptimisticConcurrencyException | DuplicateEventException ex) {
                        expectedRejections.incrementAndGet();
                    } catch (Throwable t) {
                        unexpectedError.compareAndSet(null, t);
                    } finally {
                        done.countDown();
                    }
                });
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        assertThat(unexpectedError.get()).isNull();
        assertThat(successes.get()).isEqualTo(1);
        assertThat(expectedRejections.get()).isEqualTo(contenders - 1);
        assertThat(engine.currentSequence(streamId)).isEqualTo(1L);
        assertThat(engine.stream(streamId)).containsExactly("SAME_EVENT");
    }

    /**
     * Verifies lock-free snapshot isolation while readers poll during continuous writes.
     *
     * <p>Runs 10 concurrent reader virtual threads polling
     * {@link EventStreamEngine#stream(Object)} while a writer appends 200 events,
     * asserting monotonic growth, unmodifiability, and zero concurrent exceptions.</p>
     *
     * <p><b>Executor Lifecycle Note:</b> Uses explicit {@code shutdownNow()} in
     * {@code finally} rather than {@code try-with-resources} so reader loops are
     * immediately interrupted upon failure without blocking {@link ExecutorService#close()}.</p>
     *
     * @throws InterruptedException if virtual thread coordination is interrupted
     */
    @Test
    @SuppressWarnings("resource")
    @DisplayName("Concurrency: reads during writes never throw or expose mutable state")
    void concurrentReadsDuringWrites_neverThrowOrExposeMutableState()
            throws InterruptedException {
        String streamId = "read-write-stream";
        int totalEvents = 200;
        int readerCount = 10;
        AtomicReference<Throwable> unexpectedError = new AtomicReference<>();
        AtomicBoolean writerFinished = new AtomicBoolean(false);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(readerCount + 1);

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            executor.submit(() -> {
                try {
                    start.await();
                    for (long seq = 1; seq <= totalEvents; seq++) {
                        engine.append(streamId, UUID.randomUUID(), seq, seq,
                                "EVT-" + seq);
                    }
                } catch (Throwable t) {
                    unexpectedError.compareAndSet(null, t);
                } finally {
                    writerFinished.set(true);
                    done.countDown();
                }
            });

            for (int r = 0; r < readerCount; r++) {
                executor.submit(() -> {
                    try {
                        start.await();
                        int lastSeenSize = 0;
                        while (!writerFinished.get()
                                && lastSeenSize < totalEvents
                                && unexpectedError.get() == null) {
                            var snapshot = engine.stream(streamId);
                            assertThat(snapshot)
                                    .isUnmodifiable()
                                    .hasSizeGreaterThanOrEqualTo(lastSeenSize);
                            lastSeenSize = snapshot.size();
                            Thread.yield();
                        }
                    } catch (Throwable t) {
                        unexpectedError.compareAndSet(null, t);
                    } finally {
                        done.countDown();
                    }
                });
            }

            start.countDown();
            assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        assertThat(unexpectedError.get()).isNull();
        assertThat(engine.currentSequence(streamId)).isEqualTo(totalEvents);
        assertThat(engine.stream(streamId)).hasSize(totalEvents);
    }
}
