package io.masterkun.kactor.commons.task;

import io.masterkun.kactor.commons.RuntimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A wrapper class for an {@link AsyncTask} that provides additional functionality to handle task execution and failure.
 * <p>
 * This class ensures that the wrapped task is executed only once, and it can optionally ignore failures by completing
 * the future with a default value. If the task fails and failures are not ignored, the future is completed exceptionally.
 * <p>
 * The class also logs warnings when tasks fail, providing detailed information if debug logging is enabled.
 */
class UnaryTask implements AsyncTask {
    private static final Logger LOG = LoggerFactory.getLogger(UnaryTask.class);

    private final CompletableFuture<Void> future = new CompletableFuture<>();
    private final AtomicBoolean executed = new AtomicBoolean(false);
    private final AsyncTask task;
    private final boolean ignoreFailure;

    public UnaryTask(AsyncTask task, boolean ignoreFailure) {
        this.task = task;
        this.ignoreFailure = ignoreFailure;
    }

    @Override
    public CompletableFuture<Void> run() {
        if (executed.compareAndSet(false, true)) {
            task.run().whenComplete((v, e) -> {
                if (e != null) {
                    if (ignoreFailure) {
                        future.complete(v);
                        if (LOG.isDebugEnabled()) {
                            LOG.warn("Task {} failed", task, e);
                        } else {
                            LOG.warn("Task {} failed {}", task, RuntimeUtil.toSimpleString(e));
                        }
                    } else {
                        future.completeExceptionally(e);
                    }
                } else {
                    future.complete(v);
                }
            });
        }
        return future;
    }
}
