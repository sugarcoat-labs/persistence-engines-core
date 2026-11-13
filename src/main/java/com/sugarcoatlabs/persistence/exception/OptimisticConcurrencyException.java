package com.sugarcoatlabs.persistence.exception;

/**
 * Thrown when an append operation fails optimistic sequence validation on an
 * {@link com.sugarcoatlabs.persistence.event.EventStreamEngine}.
 */
public class OptimisticConcurrencyException extends RuntimeException {

    /** The stream partition identifier where the conflict occurred. */
    private final transient Object streamId;

    /** The next sequence number expected by the caller. */
    private final long expectedSequence;

    /** The actual latest sequence number persisted in the stream. */
    private final long actualSequence;

    /**
     * Creates a new optimistic concurrency conflict exception.
     *
     * @param streamId         the stream partition identifier
     * @param expectedSequence the sequence number expected by the caller
     * @param actualSequence   the actual current sequence number of the stream
     */
    public OptimisticConcurrencyException(
            Object streamId, long expectedSequence, long actualSequence) {
        super(String.format(
                "Optimistic concurrency conflict on stream '%s': expected sequence %d, but actual is %d",
                streamId, expectedSequence, actualSequence));
        this.streamId = streamId;
        this.expectedSequence = expectedSequence;
        this.actualSequence = actualSequence;
    }

    /**
     * Returns the stream partition identifier where the conflict occurred.
     *
     * @return the {@code streamId}
     */
    public Object streamId() {
        return streamId;
    }

    /**
     * Returns the next sequence number expected by the caller.
     *
     * @return the expected sequence number
     */
    public long expectedSequence() {
        return expectedSequence;
    }

    /**
     * Returns the actual latest sequence number persisted in the stream.
     *
     * @return the actual current sequence number
     */
    public long actualSequence() {
        return actualSequence;
    }
}
