package com.sugarcoatlabs.persistence.state;

/**
 * Concrete TCK verification suite for {@link InMemoryStateStore}.
 *
 * <p>Supplies a pure-JDK {@link InMemoryStateStore} instance to
 * {@link StateStoreEngineContractTest} to verify full compliance with all CRUD,
 * snapshot immutability, validation, reset, and concurrency guarantees.</p>
 *
 * @see InMemoryStateStore
 * @see StateStoreEngineContractTest
 */
class InMemoryStateStoreTest extends StateStoreEngineContractTest<
        InMemoryStateStore<String, StateStoreEngineContractTest.DispenserConfig>> {

    private final InMemoryStateStore<String, DispenserConfig> storeUnderTest =
            new InMemoryStateStore<>();

    @Override
    protected InMemoryStateStore<String, DispenserConfig> getStore() {
        return storeUnderTest;
    }
}
