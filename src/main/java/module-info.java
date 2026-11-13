/**
 * Core pure-JDK persistence engines module for Hexagonal Architecture (Ports &amp; Adapters).
 *
 * <p>Provides zero-dependency, thread-safe in-memory storage engines behind a unified
 * static facade while strictly enforcing the Interface Segregation Principle (ISP).
 * Every exported package is restricted to public entry points, segregated contracts,
 * and domain-agnostic exceptions, while internal implementations remain encapsulated.</p>
 *
 * <h2>Exported Packages &amp; Public API</h2>
 * <ul>
 *   <li><b>{@code com.sugarcoatlabs.persistence}</b> &ndash; Exposes
 *       {@link com.sugarcoatlabs.persistence.PersistenceEnginesJdk}, the single static
 *       entry-point facade for instantiating isolated persistence engines.</li>
 *   <li><b>{@code com.sugarcoatlabs.persistence.event}</b> &ndash; Exposes
 *       {@link com.sugarcoatlabs.persistence.event.EventStreamEngine}, the segregated contract
 *       returned by {@code PersistenceEnginesJdk.eventStream()} for partitioned, append-only
 *       event streams with optimistic locking and idempotency.</li>
 *   <li><b>{@code com.sugarcoatlabs.persistence.state}</b> &ndash; Exposes
 *       {@link com.sugarcoatlabs.persistence.state.StateStoreEngine}, the segregated contract
 *       returned by {@code PersistenceEnginesJdk.stateStore()} for mutable latest-state
 *       key-value registries and projections.</li>
 *   <li><b>{@code com.sugarcoatlabs.persistence.exception}</b> &ndash; Exposes
 *       {@link com.sugarcoatlabs.persistence.exception.OptimisticConcurrencyException} and
 *       {@link com.sugarcoatlabs.persistence.exception.DuplicateEventException} so application
 *       Object Adapters can catch and translate them into domain-specific exceptions.</li>
 * </ul>
 */
module com.sugarcoatlabs.persistence {
    exports com.sugarcoatlabs.persistence;
    exports com.sugarcoatlabs.persistence.event;
    exports com.sugarcoatlabs.persistence.state;
    exports com.sugarcoatlabs.persistence.exception;
    exports com.sugarcoatlabs.persistence.lifecycle;
}
