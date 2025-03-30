package io.axor.commons.task;

/**
 * Interface for registering and managing asynchronous tasks with dependencies.
 * <p>
 * Implementations of this interface are responsible for maintaining a registry of
 * {@link DependencyTask} instances, which can have dependencies on other tasks. The tasks are
 * executed in an order that respects their defined dependencies.
 *
 * @see DependencyTask
 */
public interface DependencyTaskRegistry {
    /**
     * Registers a {@link DependencyTask} with the task registry.
     *
     * @param task the task to be registered, which must have a unique name and may define upstream
     *             dependencies
     */
    void register(DependencyTask task);
}
