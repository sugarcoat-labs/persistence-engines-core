package com.sugarcoatlabs.persistence;

/**
 * Marker interface identifying a technology-specific persistence configuration
 * pipeline or terminal engine factory.
 *
 * <p>All technology entry points (such as {@link Jdk#inMemory()} or external
 * database configurators) implement this interface so they can be passed
 * uniformly to {@link PersistenceEngines#using(PersistencePipeline)}.</p>
 */
public interface PersistencePipeline {
}
