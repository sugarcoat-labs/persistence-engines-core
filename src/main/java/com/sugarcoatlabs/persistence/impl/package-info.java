/**
 * Encapsulated, pure-JDK in-memory realizations of the core persistence engine contracts.
 *
 * <p>This package contains the concrete thread-safe implementations
 * ({@code InMemoryEventStream} and {@code InMemoryStateStore}) backing
 * {@link com.sugarcoatlabs.persistence.PersistenceEnginesJdk}.
 *
 * <p><strong>JPMS Visibility:</strong> This package is intentionally <em>not</em> exported in
 * {@code module-info.java}. External modules and application adapters must depend strictly on
 * the public interfaces ({@link com.sugarcoatlabs.persistence.event.EventStreamEngine} and
 * {@link com.sugarcoatlabs.persistence.state.StateStoreEngine}) obtained via the entry-point facade.
 *
 * @since 1.0.0
 */
package com.sugarcoatlabs.persistence.impl;
