package com.sugarcoatlabs.persistence.state;

import com.sugarcoatlabs.persistence.exception.DuplicateEntityException;
import com.sugarcoatlabs.persistence.exception.EntityNotFoundException;

import java.util.List;
import java.util.Optional;

/**
 * Jakarta Persistence (JPA) EntityManager-Style State Store View
 *
 * <p>Provides a zero-dependency, pure-JDK facade over {@link StateStoreEngine} using
 * familiar Jakarta Persistence ({@code EntityManager}) naming conventions ({@link #persist},
 * {@link #replace}, {@link #merge}, {@link #find}, {@link #findAll}, {@link #contains},
 * and {@link #remove}).</p>
 *
 * <h2>Behavioral Mapping</h2>
 * <ul>
 *   <li>{@link #persist(Object, Object)} &rarr; {@link StateStoreEngine#putIfAbsent(Object, Object)}
 *       (fails with {@link DuplicateEntityException} if {@code id} exists)</li>
 *   <li>{@link #replace(Object, Object)} &rarr; {@link StateStoreEngine#replace(Object, Object)}
 *       (fails with {@link EntityNotFoundException} if {@code id} is absent)</li>
 *   <li>{@link #merge(Object, Object)} &rarr; {@link StateStoreEngine#put(Object, Object)}
 *       (idempotent upsert)</li>
 * </ul>
 *
 * @param <ID> the unique entity identifier type
 * @param <T>  the entity or state snapshot type
 * @see StateStoreEngine
 */
public interface EntityStateStore<ID, T> {

    /**
     * Adapts any {@link StateStoreEngine} realization (in-memory, DynamoDB, PostgreSQL, etc.)
     * into a JPA-style {@link EntityStateStore}.
     *
     * @param delegate the underlying state store engine (must not be {@code null})
     * @param <ID>     the unique entity identifier type
     * @param <T>      the entity or state snapshot type
     * @return a JPA-style facade delegating atomically to {@code delegate}
     * @throws IllegalArgumentException if {@code delegate} is {@code null}
     */
    static <ID, T> EntityStateStore<ID, T> adapting(StateStoreEngine<ID, T> delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("Delegate StateStoreEngine must not be null");
        }
        return new Adapter<>(delegate);
    }

    /**
     * Atomically persists a new entity snapshot only if no entry exists for {@code id}.
     *
     * @param id     the unique identifier (must not be {@code null} or blank)
     * @param entity the entity snapshot to persist (must not be {@code null})
     * @throws IllegalArgumentException if {@code id} is {@code null} or blank, or {@code entity} is {@code null}
     * @throws DuplicateEntityException if an entry with the given {@code id} already exists
     */
    void persist(ID id, T entity);

    /**
     * Atomically replaces an existing entity snapshot associated with {@code id}.
     *
     * @param id     the unique identifier (must not be {@code null} or blank)
     * @param entity the updated entity snapshot (must not be {@code null})
     * @throws IllegalArgumentException if {@code id} is {@code null} or blank, or {@code entity} is {@code null}
     * @throws EntityNotFoundException  if no entry exists for the given {@code id}
     */
    void replace(ID id, T entity);

    /**
     * Atomically merges (inserts or overwrites) the entity snapshot associated with {@code id}.
     *
     * @param id     the unique identifier (must not be {@code null} or blank)
     * @param entity the entity snapshot to merge (must not be {@code null})
     * @throws IllegalArgumentException if {@code id} is {@code null} or blank, or {@code entity} is {@code null}
     */
    void merge(ID id, T entity);

    /**
     * Finds the entity snapshot associated with {@code id}.
     *
     * @param id the unique identifier (must not be {@code null} or blank)
     * @return an {@link Optional} containing the snapshot if present, otherwise empty
     * @throws IllegalArgumentException if {@code id} is {@code null} or blank
     */
    Optional<T> find(ID id);

    /**
     * Returns an unmodifiable snapshot of all currently stored entities.
     *
     * @return an unmodifiable list of all entities; never {@code null} (empty list if none)
     */
    List<T> findAll();

    /**
     * Checks whether the store contains an entity snapshot for {@code id}.
     *
     * @param id the unique identifier (must not be {@code null} or blank)
     * @return {@code true} if an entry exists for {@code id}, otherwise {@code false}
     * @throws IllegalArgumentException if {@code id} is {@code null} or blank
     */
    boolean contains(ID id);

    /**
     * Removes the entity snapshot associated with {@code id}, if present.
     *
     * @param id the unique identifier (must not be {@code null} or blank)
     * @return {@code true} if an entry was removed, otherwise {@code false}
     * @throws IllegalArgumentException if {@code id} is {@code null} or blank
     */
    boolean remove(ID id);

    /**
     * Internal zero-overhead adapter delegating JPA-style calls to {@link StateStoreEngine}.
     *
     * @param <ID> the unique entity identifier type
     * @param <T>  the entity or state snapshot type
     */
    final class Adapter<ID, T> implements EntityStateStore<ID, T> {

        private final StateStoreEngine<ID, T> delegate;

        private Adapter(StateStoreEngine<ID, T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void persist(ID id, T entity) {
            delegate.putIfAbsent(id, entity);
        }

        @Override
        public void replace(ID id, T entity) {
            delegate.replace(id, entity);
        }

        @Override
        public void merge(ID id, T entity) {
            delegate.put(id, entity);
        }

        @Override
        public Optional<T> find(ID id) {
            return delegate.findById(id);
        }

        @Override
        public List<T> findAll() {
            return delegate.findAll();
        }

        @Override
        public boolean contains(ID id) {
            return delegate.exists(id);
        }

        @Override
        public boolean remove(ID id) {
            return delegate.deleteById(id);
        }
    }
}
