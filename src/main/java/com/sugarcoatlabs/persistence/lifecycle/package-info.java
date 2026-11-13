/**
 * Internal Persistence Engine Lifecycle Hooks
 *
 * <p>Provides segregated lifecycle management contracts—such as {@link com.sugarcoatlabs.persistence.lifecycle.ResettableEngine}—used
 * exclusively by test harnesses and Technology Compatibility Kit (TCK) suites to reset engine state
 * deterministically between test executions.</p>
 *
 * <h2>JPMS Encapsulation &amp; Visibility</h2>
 * <p>This package is intentionally <strong>not exported</strong> in {@code module-info.java}.
 * Application modules consuming {@code com.sugarcoatlabs.persistence} interact solely with the
 * segregated production contracts ({@link com.sugarcoatlabs.persistence.event.EventStreamEngine} and
 * {@link com.sugarcoatlabs.persistence.state.StateStoreEngine}) and cannot accidentally invoke
 * destructive reset operations at runtime.</p>
 */
package com.sugarcoatlabs.persistence.lifecycle;
