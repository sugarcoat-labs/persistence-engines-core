/**
 * Unified Entry Point for Persistence Engines
 *
 * <p>Provides the universal {@link com.sugarcoatlabs.persistence.PersistenceEngines} entry
 * point, the {@link com.sugarcoatlabs.persistence.PersistencePipeline} extension contract,
 * and the pure-JDK {@link com.sugarcoatlabs.persistence.Jdk} realization for creating
 * isolated, thread-safe {@link com.sugarcoatlabs.persistence.event.EventStreamEngine} and
 * {@link com.sugarcoatlabs.persistence.state.StateStoreEngine} instances without exposing
 * internal implementation classes.</p>
 */
package com.sugarcoatlabs.persistence;
