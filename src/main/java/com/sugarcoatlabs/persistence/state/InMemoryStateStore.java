package com.sugarcoatlabs.persistence.state;

import com.sugarcoatlabs.persistence.lifecycle.ResettableEngine;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pure-JDK, thread-safe implementation of {@link StateStoreEngine}.
 *
 * <p>Backed by a {@link ConcurrentHashMap} with strict null/blank key validation
 * and defensive unmodifiable collection read boundaries.</p>
 *
 * @param <ID> the unique entity identifier type
 * @param <T>  the entity or state snapshot type
 */
public final class InMemoryStateStore<ID, T> implements StateStoreEngine<ID, T>, ResettableEngine {

    private final Map<ID, T> storage = new ConcurrentHashMap<>();

    /** Creates a new empty, thread-safe in-memory state store engine. */
    public InMemoryStateStore() {}

    @Override
    public void save(ID id, T entity) {
        validateKey(id, "id");
        if (entity == null) {
            throw new IllegalArgumentException("entity must not be null");
        }
        storage.put(id, entity);
    }

    @Override
    public Optional<T> findById(ID id) {
        validateKey(id, "id");
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public List<T> findAll() {
        return List.copyOf(storage.values());
    }

    @Override
    public boolean exists(ID id) {
        validateKey(id, "id");
        return storage.containsKey(id);
    }

    @Override
    public boolean deleteById(ID id) {
        validateKey(id, "id");
        return storage.remove(id) != null;
    }

    @Override
    public void clear() {
        storage.clear();
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
