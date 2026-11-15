package com.sugarcoatlabs.persistence.event;

import com.sugarcoatlabs.persistence.Jdk;
import com.sugarcoatlabs.persistence.PersistenceEngines;
import com.sugarcoatlabs.persistence.lifecycle.ResettableEngine;
import com.sugarcoatlabs.persistence.tck.EventStreamEngineContractTest;

import java.util.UUID;

/**
 * Pure-JDK realization runner executing the {@link EventStreamEngineContractTest} TCK
 * against {@link PersistenceEngines#using(com.sugarcoatlabs.persistence.PersistencePipeline)}
 * with {@link Jdk#inMemory()}.
 */
class InMemoryEventStreamTest
        extends EventStreamEngineContractTest<EventStreamEngine<String, UUID, String>> {

    private final EventStreamEngine<String, UUID, String> engine =
            PersistenceEngines.using(Jdk.inMemory()).eventStream();

    @Override
    protected EventStreamEngine<String, UUID, String> getEngine() {
        return engine;
    }

    @Override
    protected void resetEngine() {
        if (engine instanceof ResettableEngine resettable) {
            resettable.clear();
        }
    }
}
