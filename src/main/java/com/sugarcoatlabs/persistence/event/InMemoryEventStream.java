package com.sugarcoatlabs.persistence.event;

import com.sugarcoatlabs.persistence.lifecycle.ResettableEngine;
import com.sugarcoatlabs.persistence.exception.DuplicateEventException;
import com.sugarcoatlabs.persistence.exception.OptimisticConcurrencyException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Pure-JDK, thread-safe implementation of {@link EventStreamEngine}.
 *
 * <p>Uses per-stream striped locking via dedicated {@link ReentrantLock} partitions paired
 * with a global {@link ReentrantReadWriteLock} lifecycle barrier so writes to independent
 * streams never contend with each other, virtual threads never pin carrier threads, and
 * {@link #clear()} executes atomically without racing active appends.</p>
 *
 * @param <S>    the stream partition identifier type
 * @param <E_ID> the globally unique event identifier type
 * @param <E>    the immutable event payload type
 */
public final class InMemoryEventStream<S, E_ID, E> implements EventStreamEngine<S, E_ID, E>, ResettableEngine {

    private static final class StreamPartition {
        final ReentrantLock lock = new ReentrantLock();
        final AtomicLong sequence = new AtomicLong(0L);
    }

    private final ReentrantReadWriteLock lifecycleLock = new ReentrantReadWriteLock();
    private final Map<S, List<E>> streams = new ConcurrentHashMap<>();
    private final Map<S, StreamPartition> partitions = new ConcurrentHashMap<>();
    private final Set<E_ID> processedEventIds = ConcurrentHashMap.newKeySet();

    /** Creates a new empty, thread-safe in-memory event stream engine. */
    public InMemoryEventStream() {}

    @Override
    public void append(S streamId, E_ID eventId, long eventSequence, long expectedSequence, E event) {
        validateKey(streamId, "streamId");
        validateKey(eventId, "eventId");
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        if (expectedSequence < 1 || eventSequence < 1) {
            throw new IllegalArgumentException("Sequence numbers must be >= 1");
        }

        lifecycleLock.readLock().lock();
        try {
            StreamPartition partition = partitions.computeIfAbsent(streamId, _ -> new StreamPartition());

            partition.lock.lock();
            try {
                long current = partition.sequence.get();
                long expectedNext = current + 1L;

                if (eventSequence != expectedNext || expectedSequence != expectedNext) {
                    throw new OptimisticConcurrencyException(streamId, expectedSequence, current);
                }

                if (!processedEventIds.add(eventId)) {
                    throw new DuplicateEventException(eventId);
                }

                List<E> existing = streams.get(streamId);
                List<E> updated = (existing == null)
                        ? new ArrayList<>(1)
                        : new ArrayList<>(existing.size() + 1);
                if (existing != null) {
                    updated.addAll(existing);
                }
                updated.add(event);
                streams.put(streamId, List.copyOf(updated));

                partition.sequence.incrementAndGet();
            } finally {
                partition.lock.unlock();
            }
        } finally {
            lifecycleLock.readLock().unlock();
        }
    }

    @Override
    public long currentSequence(S streamId) {
        validateKey(streamId, "streamId");
        StreamPartition partition = partitions.get(streamId);
        return (partition == null) ? 0L : partition.sequence.get();
    }

    @Override
    public List<E> stream(S streamId) {
        validateKey(streamId, "streamId");
        return streams.getOrDefault(streamId, List.of());
    }

    @Override
    public void clear() {
        lifecycleLock.writeLock().lock();
        try {
            streams.clear();
            partitions.clear();
            processedEventIds.clear();
        } finally {
            lifecycleLock.writeLock().unlock();
        }
    }

    private static void validateKey(Object key, String paramName) {
        if (key == null) {
            throw new IllegalArgumentException(paramName + " must not be null");
        }
        if (key instanceof CharSequence cs && cs.toString().isBlank()) {
            throw new IllegalArgumentException(paramName + " must not be blank");
        }
    }
}
