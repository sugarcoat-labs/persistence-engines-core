package com.sugarcoatlabs.persistence.event;

import com.sugarcoatlabs.persistence.exception.DuplicateEventException;
import com.sugarcoatlabs.persistence.exception.OptimisticConcurrencyException;

import java.util.List;

/**
 * Append-Only Event Stream Engine Contract
 *
 * <p>Defines a partitioned, append-only event log with optimistic concurrency
 * control (monotonic 1-indexed sequence numbers) and idempotency deduplication.</p>
 *
 * @param <S>    the stream partition identifier type (must properly implement {@code equals}/{@code hashCode})
 * @param <E_ID> the globally unique event identifier type (must properly implement {@code equals}/{@code hashCode})
 * @param <E>    the immutable event payload type
 */
public interface EventStreamEngine<S, E_ID, E> {

    /**
     * Atomically appends an event to the target stream if and only if:
     * <ol>
     *   <li>{@code eventSequence} and {@code expectedSequence} both equal {@code currentSequence(streamId) + 1}.</li>
     *   <li>{@code eventId} has never been persisted before.</li>
     * </ol>
     *
     * @param streamId         the stream partition identifier (must not be {@code null} or blank)
     * @param eventId          the globally unique event identifier (must not be {@code null} or blank)
     * @param eventSequence    the sequence number embedded in the event (must be &ge; 1)
     * @param expectedSequence the expected next sequence number (must be &ge; 1)
     * @param event            the event payload to persist (must not be {@code null})
     * @throws OptimisticConcurrencyException if sequence validation fails
     * @throws DuplicateEventException        if {@code eventId} was already persisted
     * @throws IllegalArgumentException       if any argument is {@code null}, blank, or sequence &lt; 1
     */
    void append(S streamId, E_ID eventId, long eventSequence, long expectedSequence, E event);

    /**
     * Returns the highest persisted sequence number for the given stream,
     * or {@code 0L} if no events exist for that stream yet.
     *
     * @param streamId the stream partition identifier (must not be {@code null} or blank)
     * @return the current highest sequence number, or {@code 0L} if none
     * @throws IllegalArgumentException if {@code streamId} is {@code null} or blank
     */
    long currentSequence(S streamId);

    /**
     * Returns an unmodifiable chronological snapshot (oldest to newest) of all events
     * persisted in the given stream.
     *
     * @param streamId the stream partition identifier (must not be {@code null} or blank)
     * @return an unmodifiable list of events, or an empty list if none exist
     * @throws IllegalArgumentException if {@code streamId} is {@code null} or blank
     */
    List<E> stream(S streamId);
}
