package com.sugarcoatlabs.persistence.exception;

/**
 * Thrown when an operation in a {@link com.sugarcoatlabs.persistence.state.StateStoreEngine}
 * targets an {@code id} that does not exist in the store.
 */
public class EntityNotFoundException extends RuntimeException {

    /** The missing entity identifier that triggered this exception. */
    private final transient Object id;

    /**
     * Creates a new exception for the given missing entity identifier.
     *
     * @param id the unique entity identifier that was not found in the store
     */
    public EntityNotFoundException(Object id) {
        super("Entity not found for id: " + id);
        this.id = id;
    }

    /**
     * Returns the missing entity identifier.
     *
     * @return the missing {@code id}
     */
    public Object id() {
        return id;
    }
}
