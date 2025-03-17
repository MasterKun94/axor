package io.masterkun.axor.commons.task;

import java.util.concurrent.CompletableFuture;

/**
 * Represents an asynchronous task that can be executed. Implementations of this interface
 * provide a method to run the task asynchronously, returning a {@link CompletableFuture}
 * that completes when the task is done.
 *
 * <p>The {@link #run()} method is expected to initiate the execution of the task and return
 * a {@link CompletableFuture} that will be completed once the task has finished. The
 * {@link CompletableFuture} can also be used to handle the result or any exceptions that
 * may occur during the execution of the task.
 */
public interface AsyncTask {
    CompletableFuture<Void> run();
}
