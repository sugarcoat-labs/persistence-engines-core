package com.sugarcoatlabs.persistence.state;

import java.util.List;
import java.util.Optional;

/**
 * Mutable Latest-State Store Engine Contract
 *
 * <p>Defines a thread-safe, key-value state store designed for configuration
 * registries, entity snapshots, and CQRS read projections where only the
 * latest state per identifier is retained.</p>
 *
 * @param <ID> the unique entity identifier type (must properly implement {@code equals}/{@code hashCode})
 * @param <T>  the entity or state snapshot type
 */
public interface StateStoreEngine<ID, T> {

    /**
     * Stores or overwrites the state snapshot associated with the given identifier.
     *
     * @param id     the unique identifier (must not be {@code null} or blank)
     * @param entity the state snapshot to store (must not be {@code null})
     * @throws IllegalArgumentException if {@code id} is {@code null} or blank, or {@code entity} is {@code null}
     */
    void save(ID id, T entity);

    /**
     * Retrieves the state snapshot associated with the given identifier.
     *
     * @param id the unique identifier (must not be {@code null} or blank)
     * @return an {@link Optional} containing the snapshot if present, otherwise empty
     * @throws IllegalArgumentException if {@code id} is {@code null} or blank
     */
    Optional<T> findById(ID id);

    /**
     * Returns an unmodifiable snapshot of all currently stored state entities.
     *
     * @return an unmodifiable list of all entities; never {@code null} (empty list if none)
     */
    List<T> findAll();

    /**
     * Checks whether a state snapshot exists for the given identifier.
     *
     * @param id the unique identifier (must not be {@code null} or blank)
     * @return {@code true} if an entry exists for {@code id}, otherwise {@code false}
     * @throws IllegalArgumentException if {@code id} is {@code null} or blank
     */
    boolean exists(ID id);

    /**
     * Removes the state snapshot associated with the given identifier, if present.
     *
     * @param id the unique identifier (must not be {@code null} or blank)
     * @return {@code true} if an entry was removed, otherwise {@code false}
     * @throws IllegalArgumentException if {@code id} is {@code null} or blank
     */
    boolean deleteById(ID id);
}
