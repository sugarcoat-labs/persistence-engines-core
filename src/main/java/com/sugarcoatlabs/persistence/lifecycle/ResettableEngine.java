package com.sugarcoatlabs.persistence.lifecycle;

/**
 * Resettable Persistence Engine Lifecycle Hook
 *
 * <p>Segregated capability interface for persistence engines that support deterministic,
 * atomic state resets between test executions without recreating engine instances.</p>
 *
 * <h2>Architectural Purpose</h2>
 * <ul>
 *   <li><b>Protects Production Contracts (ISP):</b> Keeps destructive wipe operations out of
 *       {@link com.sugarcoatlabs.persistence.event.EventStreamEngine} and
 *       {@link com.sugarcoatlabs.persistence.state.StateStoreEngine}, ensuring application code
 *       never sees or invokes {@link #clear()}.</li>
 *   <li><b>Enforces TCK Test Isolation:</b> Serves as an intersection type bound in abstract
 *       contract test suites so {@code @BeforeEach} hooks can guarantee zero cross-test state
 *       leakage across reused engine instances.</li>
 * </ul>
 *
 * <h2>Encapsulation Guarantee</h2>
 * <p>Because {@code com.sugarcoatlabs.persistence.lifecycle} is omitted from {@code module-info.java},
 * this interface is inaccessible to external JPMS modules in production and remains visible only
 * within this module and its patched test classpath.</p>
 */
public interface ResettableEngine {

    /**
     * Atomically removes all persisted entries, sequence counters, and deduplication
     * metadata from the engine.
     *
     * <p>Intended exclusively for deterministic resets between test executions.</p>
     */
    void clear();
}
