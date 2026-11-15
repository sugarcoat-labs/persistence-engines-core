/**
 * Portable Technology Compatibility Kit (TCK) for Persistence Engines
 *
 * <p>Provides abstract behavioral contract test suites
 * ({@link com.sugarcoatlabs.persistence.tck.EventStreamEngineContractTest} and
 * {@link com.sugarcoatlabs.persistence.tck.StateStoreEngineContractTest}) packaged into
 * the published {@code test-jar} artifact.</p>
 *
 * <p>External database realization modules extend these classes to verify 100% behavioral
 * parity across optimistic concurrency, global idempotency, read-your-writes consistency,
 * and immutability boundaries without duplicating test logic.</p>
 */
package com.sugarcoatlabs.persistence.tck;
