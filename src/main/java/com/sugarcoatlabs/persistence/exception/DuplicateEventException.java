package com.sugarcoatlabs.persistence.exception;

/**
 * Thrown when an event with an already processed {@code eventId} is submitted to an
 * {@link com.sugarcoatlabs.persistence.event.EventStreamEngine}.
 */
public class DuplicateEventException extends RuntimeException {

    /** The duplicate event identifier that triggered this exception. */
    private final transient Object eventId;

    /**
     * Creates a new exception for the given duplicate event identifier.
     *
     * @param eventId the globally unique event identifier that was already persisted
     */
    public DuplicateEventException(Object eventId) {
        super("Duplicate event detected for eventId: " + eventId);
        this.eventId = eventId;
    }

    /**
     * Returns the duplicate event identifier.
     *
     * @return the duplicate {@code eventId}
     */
    public Object eventId() {
        return eventId;
    }
}
