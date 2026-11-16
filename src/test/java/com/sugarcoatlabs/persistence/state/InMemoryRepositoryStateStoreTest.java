package com.sugarcoatlabs.persistence.state;

import com.sugarcoatlabs.persistence.Jdk;
import com.sugarcoatlabs.persistence.PersistenceEngines;
import com.sugarcoatlabs.persistence.lifecycle.ResettableEngine;
import com.sugarcoatlabs.persistence.tck.RepositoryStateStoreContractTest;
import com.sugarcoatlabs.persistence.tck.StateStoreEngineContractTest;

/**
 * Pure-JDK realization runner executing the {@link RepositoryStateStoreContractTest} TCK
 * against {@link RepositoryStateStore#adapting(StateStoreEngine)} backed by
 * {@link PersistenceEngines#using(com.sugarcoatlabs.persistence.PersistencePipeline)}
 * with {@link Jdk#inMemory()}.
 */
class InMemoryRepositoryStateStoreTest
        extends RepositoryStateStoreContractTest<
        RepositoryStateStore<String, StateStoreEngineContractTest.DispenserConfig>> {

    private final StateStoreEngine<String, StateStoreEngineContractTest.DispenserConfig> delegate =
            PersistenceEngines.using(Jdk.inMemory()).stateStore();

    private final RepositoryStateStore<String, StateStoreEngineContractTest.DispenserConfig> repository =
            RepositoryStateStore.adapting(delegate);

    @Override
    protected RepositoryStateStore<String, StateStoreEngineContractTest.DispenserConfig> getRepository() {
        return repository;
    }

    @Override
    protected void resetStore() {
        if (delegate instanceof ResettableEngine resettable) {
            resettable.clear();
        }
    }
}
