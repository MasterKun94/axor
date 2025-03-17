package io.masterkun.kactor.commons.task;

import io.masterkun.kactor.commons.StringUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * A class that manages and executes a set of asynchronous tasks with dependencies.
 * <p>
 * This class implements both the {@link DependencyTaskRegistry} and {@link AsyncTask} interfaces,
 * allowing it to register tasks and execute them in an order that respects their dependencies.
 * The tasks are represented by instances of {@link DependencyTask}, which can define upstream
 * dependencies. The execution of tasks is managed using {@link CompletableFuture} to handle
 * asynchronous operations.
 *
 * @see DependencyTask
 * @see DependencyTaskRegistry
 * @see AsyncTask
 */
public class DependencyTaskRegistryRunner implements DependencyTaskRegistry, AsyncTask {
    private final boolean continueOnFailure;
    private final Map<String, UnaryTask> tasks = new HashMap<>();
    private final Map<String, List<String>> dependencies = new HashMap<>();
    private final List<String> rootTasks = new ArrayList<>();

    public DependencyTaskRegistryRunner(boolean continueOnFailure) {
        this.continueOnFailure = continueOnFailure;
    }

    public void register(DependencyTask task) {
        UnaryTask unaryTask = new UnaryTask(task, continueOnFailure);
        String taskName = task.name();
        if (StringUtil.isEmpty(taskName)) {
            throw new IllegalArgumentException("Task name cannot be empty");
        }
        if (tasks.putIfAbsent(taskName, unaryTask) != null) {
            throw new RuntimeException("Duplicate task name: " + taskName);
        }
        dependencies.put(taskName, new ArrayList<>());
        if (task.dependencyDownstream() == null || task.dependencyDownstream().isEmpty()) {
            rootTasks.add(taskName);
        } else {
            for (String downstream : task.dependencyDownstream()) {
                List<String> dependencyList = dependencies.get(downstream);
                if (dependencyList == null) {
                    throw new RuntimeException("Dependency downstream " + downstream + " not found");
                }
                dependencyList.add(taskName);
            }
        }
    }

    private CompletableFuture<Void> runTask(String taskName) {
        UnaryTask unaryTask = tasks.get(taskName);
        assert unaryTask != null;
        List<String> dependencyList = dependencies.get(taskName);
        assert dependencyList != null;
        if (dependencyList.isEmpty()) {
            return unaryTask.run();
        }
        return CompletableFuture
                .allOf(dependencyList.stream()
                        .map(this::runTask)
                        .toArray(CompletableFuture[]::new))
                .thenCompose(v -> unaryTask.run());
    }

    @Override
    public CompletableFuture<Void> run() {
        return CompletableFuture.allOf(rootTasks.stream()
                .map(this::runTask)
                .toArray(CompletableFuture[]::new));
    }
}
