/**
 * Append-Only Event Stream Contracts &amp; In-Memory Engine
 *
 * <p>Defines the partitioned {@link com.sugarcoatlabs.persistence.event.EventStreamEngine}
 * contract for 1-indexed monotonic event logs with optimistic concurrency control and
 * global event-ID idempotency, alongside its striped-lock pure-JDK implementation.</p>
 */
package com.sugarcoatlabs.persistence.event;
