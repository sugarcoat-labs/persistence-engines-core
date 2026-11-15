package com.sugarcoatlabs.persistence.state;

import com.sugarcoatlabs.persistence.Jdk;
import com.sugarcoatlabs.persistence.PersistenceEngines;
import com.sugarcoatlabs.persistence.lifecycle.ResettableEngine;
import com.sugarcoatlabs.persistence.tck.StateStoreEngineContractTest;

/**
 * Pure-JDK realization runner executing the {@link StateStoreEngineContractTest} TCK
 * against {@link PersistenceEngines#using(com.sugarcoatlabs.persistence.PersistencePipeline)}
 * with {@link Jdk#inMemory()}.
 */
class InMemoryStateStoreTest
        extends StateStoreEngineContractTest<
        StateStoreEngine<String, StateStoreEngineContractTest.DispenserConfig>> {

    private final StateStoreEngine<String, DispenserConfig> store =
            PersistenceEngines.using(Jdk.inMemory()).stateStore();

    @Override
    protected StateStoreEngine<String, DispenserConfig> getStore() {
        return store;
    }

    @Override
    protected void resetStore() {
        if (store instanceof ResettableEngine resettable) {
            resettable.clear();
        }
    }
}
