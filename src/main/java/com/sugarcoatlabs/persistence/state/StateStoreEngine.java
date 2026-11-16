package com.sugarcoatlabs.persistence.state;

import com.sugarcoatlabs.persistence.exception.DuplicateEntityException;
import com.sugarcoatlabs.persistence.exception.EntityNotFoundException;

import java.util.List;
import java.util.Optional;

/**
 * Mutable Latest-State Store Engine Contract
 *
 * <p>Defines a thread-safe, key-value state store designed for configuration
 * registries, entity snapshots, and CQRS read projections where only the
 * latest state per identifier is retained.</p>
 *
 * <h2>Behavioral Standard &amp; Cross-Database Feasibility</h2>
 * <p>Write operations ({@link #putIfAbsent}, {@link #replace}, and {@link #put}) follow
 * the canonical {@link java.util.concurrent.ConcurrentMap} and JSR-107 (JCache)
 * key-value semantics while signaling conflict or missing-key failures via domain-agnostic
 * exceptions. Each operation maps directly to a single atomic primitive across mainstream
 * database engines—including Amazon DynamoDB ({@code PutItem} with {@code attribute_not_exists}
 * or {@code attribute_exists}), PostgreSQL ({@code INSERT}, {@code UPDATE}, {@code ON CONFLICT}),
 * MySQL ({@code INSERT}, {@code UPDATE}, {@code ON DUPLICATE KEY UPDATE}), and MongoDB
 * ({@code insertOne}, {@code replaceOne})—without requiring distributed locks or multi-step
 * transactions.</p>
 *
 * <p><strong>Design Note on Compare-And-Swap (CAS):</strong> The three-argument
 * {@code replace(id, expectedOldEntity, newEntity)} variant is deliberately omitted.
 * Enforcing full-value equality across serialized payloads (such as JSON documents) is fragile
 * across heterogeneous database engines; use
 * {@link com.sugarcoatlabs.persistence.event.EventStreamEngine} when strict optimistic
 * concurrency control is required.</p>
 *
 * @param <ID> the unique entity identifier type (must properly implement {@code equals}/{@code hashCode})
 * @param <T>  the entity or state snapshot type
 */
public interface StateStoreEngine<ID, T> {

    /**
     * Atomically stores the state snapshot only if no entry currently exists for the given identifier.
     *
     * @param id     the unique identifier (must not be {@code null} or blank)
     * @param entity the state snapshot to store (must not be {@code null})
     * @throws IllegalArgumentException if {@code id} is {@code null} or blank, or {@code entity} is {@code null}
     * @throws DuplicateEntityException if an entry with the given {@code id} already exists
     */
    void putIfAbsent(ID id, T entity);

    /**
     * Atomically replaces the existing state snapshot associated with the given identifier.
     *
     * @param id     the unique identifier (must not be {@code null} or blank)
     * @param entity the updated state snapshot to store (must not be {@code null})
     * @throws IllegalArgumentException if {@code id} is {@code null} or blank, or {@code entity} is {@code null}
     * @throws EntityNotFoundException  if no entry exists for the given {@code id}
     */
    void replace(ID id, T entity);

    /**
     * Atomically stores or overwrites the state snapshot associated with the given identifier.
     *
     * @param id     the unique identifier (must not be {@code null} or blank)
     * @param entity the state snapshot to store (must not be {@code null})
     * @throws IllegalArgumentException if {@code id} is {@code null} or blank, or {@code entity} is {@code null}
     */
    void put(ID id, T entity);

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
