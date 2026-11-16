package com.sugarcoatlabs.persistence.exception;

/**
 * Thrown when an entity with an already existing {@code id} is submitted to a
 * {@link com.sugarcoatlabs.persistence.state.StateStoreEngine}.
 */
public class DuplicateEntityException extends RuntimeException {

    /** The duplicate entity identifier that triggered this exception. */
    private final transient Object id;

    /**
     * Creates a new exception for the given duplicate entity identifier.
     *
     * @param id the unique entity identifier that already exists in the store
     */
    public DuplicateEntityException(Object id) {
        super("Duplicate entity detected for id: " + id);
        this.id = id;
    }

    /**
     * Returns the duplicate entity identifier.
     *
     * @return the duplicate {@code id}
     */
    public Object id() {
        return id;
    }
}
