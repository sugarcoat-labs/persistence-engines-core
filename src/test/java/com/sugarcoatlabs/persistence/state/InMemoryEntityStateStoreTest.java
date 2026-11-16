package com.sugarcoatlabs.persistence.state;

import com.sugarcoatlabs.persistence.Jdk;
import com.sugarcoatlabs.persistence.PersistenceEngines;
import com.sugarcoatlabs.persistence.lifecycle.ResettableEngine;
import com.sugarcoatlabs.persistence.tck.EntityStateStoreContractTest;
import com.sugarcoatlabs.persistence.tck.StateStoreEngineContractTest;

/**
 * Pure-JDK realization runner executing the {@link EntityStateStoreContractTest} TCK
 * against {@link EntityStateStore#adapting(StateStoreEngine)} backed by
 * {@link PersistenceEngines#using(com.sugarcoatlabs.persistence.PersistencePipeline)}
 * with {@link Jdk#inMemory()}.
 */
class InMemoryEntityStateStoreTest
        extends EntityStateStoreContractTest<
        EntityStateStore<String, StateStoreEngineContractTest.DispenserConfig>> {

    private final StateStoreEngine<String, StateStoreEngineContractTest.DispenserConfig> delegate =
            PersistenceEngines.using(Jdk.inMemory()).stateStore();

    private final EntityStateStore<String, StateStoreEngineContractTest.DispenserConfig> entityStore =
            EntityStateStore.adapting(delegate);

    @Override
    protected EntityStateStore<String, StateStoreEngineContractTest.DispenserConfig> getEntityStore() {
        return entityStore;
    }

    @Override
    protected void resetStore() {
        if (delegate instanceof ResettableEngine resettable) {
            resettable.clear();
        }
    }
}
