package com.sugarcoatlabs.persistence.event;

import com.sugarcoatlabs.persistence.exception.OptimisticConcurrencyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concrete TCK verification suite for {@link InMemoryEventStream}.
 *
 * <p>Inherits and executes the full behavioral contract defined in
 * {@link EventStreamEngineContractTest}, and supplements it with white-box concurrency
 * tests targeting internal pure-JDK synchronization guarantees (striped partition locks
 * and global lifecycle read-write barriers) that do not apply to external database adapters.</p>
 *
 * @see InMemoryEventStream
 * @see EventStreamEngineContractTest
 */
class InMemoryEventStreamTest extends EventStreamEngineContractTest<InMemoryEventStream<String, UUID, String>> {

    private final InMemoryEventStream<String, UUID, String> engineUnderTest =
            new InMemoryEventStream<>();

    @Override
    protected InMemoryEventStream<String, UUID, String> getEngine() {
        return engineUnderTest;
    }

    /**
     * Verifies internal lifecycle lock safety when {@link InMemoryEventStream#clear()}
     * executes concurrently alongside active {@link InMemoryEventStream#append} operations.
     *
     * <h3>What is tested</h3>
     * <p>Launches 50 concurrent writer virtual threads appending events to a shared stream
     * while 10 concurrent reset virtual threads invoke {@code clear()}, then verifies that
     * subsequent sequential appends operate cleanly on a consistent partition state.</p>
     *
     * <h3>Why this test lives here (and not in {@link EventStreamEngineContractTest})</h3>
     * <ul>
     *   <li><b>Implementation-specific guarantee:</b> Validates that the internal
     *       {@code ReentrantReadWriteLock} lifecycle barrier prevents orphaned
     *       {@code StreamPartition} locks or stale writes from leaking across resets.</li>
     *   <li><b>TCK portability:</b> External database adapters (e.g., PostgreSQL, DynamoDB)
     *       cannot realistically execute full-table truncations concurrently with active
     *       transactions without lock contention or aborts, so requiring this in the abstract
     *       TCK would impose an unrealistic constraint on production storage engines.</li>
     * </ul>
     *
     * @throws InterruptedException if virtual thread coordination is interrupted
     */
    @Test
    @DisplayName("InMemory Concurrency: concurrent clear() during active appends never corrupts partition state")
    void concurrentClearDuringAppends_maintainsConsistentPartitionState() throws InterruptedException {
        String streamId = "volatile-stream";
        int writers = 50;
        int clearCalls = 10;
        AtomicReference<Throwable> unexpectedError = new AtomicReference<>();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(writers + clearCalls);

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            for (int w = 0; w < writers; w++) {
                executor.submit(() -> {
                    try {
                        start.await();
                        long nextSeq = engineUnderTest.currentSequence(streamId) + 1;
                        engineUnderTest.append(streamId, UUID.randomUUID(), nextSeq, nextSeq, "EVT");
                    } catch (OptimisticConcurrencyException ignored) {
                        // Expected when racing with other writers or a concurrent reset
                    } catch (Throwable t) {
                        unexpectedError.compareAndSet(null, t);
                    } finally {
                        done.countDown();
                    }
                });
            }

            for (int c = 0; c < clearCalls; c++) {
                executor.submit(() -> {
                    try {
                        start.await();
                        engineUnderTest.clear();
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
        engineUnderTest.clear();
        engineUnderTest.append(streamId, UUID.randomUUID(), 1L, 1L, "AFTER_RESET");
        assertThat(engineUnderTest.currentSequence(streamId)).isEqualTo(1L);
        assertThat(engineUnderTest.stream(streamId)).containsExactly("AFTER_RESET");
    }
}
