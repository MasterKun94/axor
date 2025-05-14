package io.axor.raft.file;

import com.google.protobuf.ByteString;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class FileWriteSubscriber implements Flow.Subscriber<ByteString> {
    private static final Logger LOG = LoggerFactory.getLogger(FileWriteSubscriber.class);

    private final File targetFile;
    private final BlockingQueue<Runnable> queue;
    private final CompletableFuture<Void> future;
    private Flow.Subscription subscription;
    private boolean complete;
    private OutputStream out;

    public FileWriteSubscriber(File targetFile) {
        this.targetFile = targetFile;
        this.queue = new LinkedBlockingQueue<>();
        this.future = new CompletableFuture<>();
        new FileWriteThread().start();
    }

    public CompletableFuture<Void> getFuture() {
        return future;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        queue.add(() -> {
            this.subscription = subscription;
            LOG.info("File write [{}] start", targetFile);
            try {
                out = FileUtils.openOutputStream(targetFile);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public void onNext(ByteString data) {
        queue.add(() -> {
            try {
                data.writeTo(out);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public void onComplete() {
        queue.add(() -> {
            try {
                out.flush();
                out.close();
            } catch (IOException e) {
                try {
                    out.close();
                } catch (IOException ex) {
                    // ignore
                }
                throw new RuntimeException(e);
            }
            complete = true;
            future.complete(null);
            LOG.info("File write [{}] complete", targetFile);
        });
    }

    @Override
    public void onError(Throwable error) {
        queue.add(() -> {
            complete = true;
            future.completeExceptionally(error);
            LOG.info("File write [{}] error", targetFile, error);
        });
    }

    private class FileWriteThread extends Thread {
        @Override
        public void run() {
            try {
                while (!complete) {
                    Runnable poll = queue.poll(1000, TimeUnit.MILLISECONDS);
                    if (poll != null) {
                        poll.run();
                    }
                }
                assert future.isDone();
            } catch (Throwable e) {
                LOG.info("File write [{}] unexpected handler error", targetFile, e);
                try {
                    complete = true;
                    future.completeExceptionally(e);
                } finally {
                    subscription.cancel();
                }
            }
            LOG.info("File write [{}] thread stop", targetFile);
        }
    }
}
