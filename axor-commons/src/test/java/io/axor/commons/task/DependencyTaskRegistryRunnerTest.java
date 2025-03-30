package io.axor.commons.task;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DependencyTaskRegistryRunnerTest {

    @Test
    public void test() {
        DependencyTaskRegistryRunner registryRunner = new DependencyTaskRegistryRunner(true);
        Task task1 = new Task("task1");
        Task task2 = new Task("task2", task1);
        Task task3 = new Task("task3", task2);
        Task task4 = new Task("task4");
        Task task5 = new Task("task5", task3, task4);
        Task task6 = new Task("task6", task5);
        Task task7 = new Task("task7", task1, task2, task3, task4, task5, task6);
        Task task8 = new Task("task8", task1, task2);
        Task task9 = new Task("task9", task7, task8);
        Task task10 = new Task("task10", task1, task2, task3, task4, task5, task6, task7);
        registryRunner.register(task1);
        registryRunner.register(task2);
        registryRunner.register(task3);
        registryRunner.register(task4);
        registryRunner.register(task5);
        registryRunner.register(task6);
        registryRunner.register(task7);
        registryRunner.register(task8);
        registryRunner.register(task9);
        registryRunner.register(task10);
        registryRunner.run().join();
        assertTrue(task1.done);
        assertTrue(task2.done);
        assertTrue(task3.done);
        assertTrue(task4.done);
        assertTrue(task5.done);
        assertTrue(task6.done);
        assertTrue(task7.done);
        assertTrue(task8.done);
        assertTrue(task9.done);
        assertTrue(task10.done);
    }

    public static class Task extends DependencyTask {
        private final List<Task> dependencyTasks;
        private volatile boolean done;

        public Task(String name, Task... dependencyUpstream) {
            super(name, Arrays.stream(dependencyUpstream).map(Task::name).toArray(String[]::new));
            dependencyTasks = List.of(dependencyUpstream);
        }

        @Override
        public CompletableFuture<Void> run() {
            assertFalse(done);
            done = true;
            for (Task task : dependencyTasks) {
                assertFalse(task.done);
            }
            return CompletableFuture.completedFuture(null);
        }
    }
}
