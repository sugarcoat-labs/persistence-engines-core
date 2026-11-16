package com.sugarcoatlabs.persistence.state;

import com.sugarcoatlabs.persistence.exception.DuplicateEntityException;
import com.sugarcoatlabs.persistence.exception.EntityNotFoundException;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data Repository-Style State Store View
 *
 * <p>Provides a zero-dependency, pure-JDK facade over {@link StateStoreEngine} using
 * familiar Spring Data naming conventions ({@link #insert}, {@link #update}, {@link #save},
 * {@link #findById}, {@link #findAll}, {@link #existsById}, and {@link #deleteById}).</p>
 *
 * <h2>Behavioral Mapping</h2>
 * <ul>
 *   <li>{@link #insert(Object, Object)} &rarr; {@link StateStoreEngine#putIfAbsent(Object, Object)}
 *       (fails with {@link DuplicateEntityException} if {@code id} exists)</li>
 *   <li>{@link #update(Object, Object)} &rarr; {@link StateStoreEngine#replace(Object, Object)}
 *       (fails with {@link EntityNotFoundException} if {@code id} is absent)</li>
 *   <li>{@link #save(Object, Object)} &rarr; {@link StateStoreEngine#put(Object, Object)}
 *       (idempotent upsert)</li>
 * </ul>
 *
 * @param <ID> the unique entity identifier type
 * @param <T>  the entity or state snapshot type
 * @see StateStoreEngine
 */
public interface RepositoryStateStore<ID, T> {

    /**
     * Adapts any {@link StateStoreEngine} realization (in-memory, DynamoDB, PostgreSQL, etc.)
     * into a Spring Data-style {@link RepositoryStateStore}.
     *
     * @param delegate the underlying state store engine (must not be {@code null})
     * @param <ID>     the unique entity identifier type
     * @param <T>      the entity or state snapshot type
     * @return a repository-style facade delegating atomically to {@code delegate}
     * @throws IllegalArgumentException if {@code delegate} is {@code null}
     */
    static <ID, T> RepositoryStateStore<ID, T> adapting(StateStoreEngine<ID, T> delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("Delegate StateStoreEngine must not be null");
        }
        return new Adapter<>(delegate);
    }

    /**
     * Atomically inserts a new entity snapshot only if no entry exists for {@code id}.
     *
     * @param id     the unique identifier (must not be {@code null} or blank)
     * @param entity the entity snapshot to insert (must not be {@code null})
     * @throws IllegalArgumentException if {@code id} is {@code null} or blank, or {@code entity} is {@code null}
     * @throws DuplicateEntityException if an entry with the given {@code id} already exists
     */
    void insert(ID id, T entity);

    /**
     * Atomically updates an existing entity snapshot associated with {@code id}.
     *
     * @param id     the unique identifier (must not be {@code null} or blank)
     * @param entity the updated entity snapshot (must not be {@code null})
     * @throws IllegalArgumentException if {@code id} is {@code null} or blank, or {@code entity} is {@code null}
     * @throws EntityNotFoundException  if no entry exists for the given {@code id}
     */
    void update(ID id, T entity);

    /**
     * Atomically inserts or overwrites the entity snapshot associated with {@code id}.
     *
     * @param id     the unique identifier (must not be {@code null} or blank)
     * @param entity the entity snapshot to save (must not be {@code null})
     * @throws IllegalArgumentException if {@code id} is {@code null} or blank, or {@code entity} is {@code null}
     */
    void save(ID id, T entity);

    /**
     * Retrieves the entity snapshot associated with {@code id}.
     *
     * @param id the unique identifier (must not be {@code null} or blank)
     * @return an {@link Optional} containing the snapshot if present, otherwise empty
     * @throws IllegalArgumentException if {@code id} is {@code null} or blank
     */
    Optional<T> findById(ID id);

    /**
     * Returns an unmodifiable snapshot of all currently stored entities.
     *
     * @return an unmodifiable list of all entities; never {@code null} (empty list if none)
     */
    List<T> findAll();

    /**
     * Checks whether an entity snapshot exists for {@code id}.
     *
     * @param id the unique identifier (must not be {@code null} or blank)
     * @return {@code true} if an entry exists for {@code id}, otherwise {@code false}
     * @throws IllegalArgumentException if {@code id} is {@code null} or blank
     */
    boolean existsById(ID id);

    /**
     * Removes the entity snapshot associated with {@code id}, if present.
     *
     * @param id the unique identifier (must not be {@code null} or blank)
     * @return {@code true} if an entry was removed, otherwise {@code false}
     * @throws IllegalArgumentException if {@code id} is {@code null} or blank
     */
    boolean deleteById(ID id);

    /**
     * Internal zero-overhead adapter delegating Spring Data-style calls to {@link StateStoreEngine}.
     *
     * @param <ID> the unique entity identifier type
     * @param <T>  the entity or state snapshot type
     */
    final class Adapter<ID, T> implements RepositoryStateStore<ID, T> {

        private final StateStoreEngine<ID, T> delegate;

        private Adapter(StateStoreEngine<ID, T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void insert(ID id, T entity) {
            delegate.putIfAbsent(id, entity);
        }

        @Override
        public void update(ID id, T entity) {
            delegate.replace(id, entity);
        }

        @Override
        public void save(ID id, T entity) {
            delegate.put(id, entity);
        }

        @Override
        public Optional<T> findById(ID id) {
            return delegate.findById(id);
        }

        @Override
        public List<T> findAll() {
            return delegate.findAll();
        }

        @Override
        public boolean existsById(ID id) {
            return delegate.exists(id);
        }

        @Override
        public boolean deleteById(ID id) {
            return delegate.deleteById(id);
        }
    }
}
