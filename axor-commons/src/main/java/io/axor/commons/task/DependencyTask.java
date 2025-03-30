package io.axor.commons.task;

import java.util.List;

/**
 * Abstract base class for tasks that can have dependencies on other tasks.
 * <p>
 * This class implements the {@link AsyncTask} interface, providing a foundation for creating tasks
 * that can be executed asynchronously. Each task is identified by a unique name and can define
 * downstream dependencies, which are tasks that should be executed after this task.
 * <p>
 * Subclasses must implement the {@link #name()} method to provide a unique name for the task. The
 * {@link #dependencyDownstream()} method can be overridden to specify the names of downstream
 * tasks, but it defaults to an empty list if not overridden.
 *
 * @see AsyncTask
 */
public abstract class DependencyTask implements AsyncTask {
    private final String name;
    private final List<String> dependencyDownstream;

    protected DependencyTask(String name, String... dependency) {
        this.name = name;
        this.dependencyDownstream = List.of(dependency);
    }

    public final String name() {
        return name;
    }

    /**
     * Returns a list of names of tasks that should be executed after this task.
     *
     * @return a list of strings representing the names of downstream tasks
     */
    public final List<String> dependencyDownstream() {
        return dependencyDownstream;
    }

    @Override
    public String toString() {
        return "Task[" + name() + "]";
    }
}
