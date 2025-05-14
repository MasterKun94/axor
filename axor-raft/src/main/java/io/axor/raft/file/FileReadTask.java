package io.axor.raft.file;

import com.google.protobuf.UnsafeByteOperations;
import io.axor.api.ActorRef;
import io.axor.api.impl.ActorUnsafe;
import io.axor.raft.proto.FileManagerProto;
import io.axor.raft.proto.FileManagerProto.FileClientMessage;
import io.axor.raft.proto.FileManagerProto.FileServerMessage;
import io.axor.runtime.Signal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class FileReadTask {
    private static final Logger LOG = LoggerFactory.getLogger(FileReadTask.class);

    private final String taskId;
    private final ScheduledExecutorService executor;
    private final TokenBucket bucket;
    private final File file;
    private final ActorRef<FileServerMessage> server;
    private final ActorRef<FileClientMessage> client;
    private volatile boolean running = true;

    public FileReadTask(String taskId, ScheduledExecutorService executor,
                        TokenBucket bucket,
                        File file,
                        ActorRef<FileServerMessage> server,
                        ActorRef<FileClientMessage> client) {
        this.taskId = taskId;
        this.executor = executor;
        this.bucket = bucket;
        this.file = file;
        this.server = server;
        this.client = client;
    }

    public void run() {
        executor.submit(() -> {
            FileInputStream stream;
            try {
                stream = new FileInputStream(file);
            } catch (FileNotFoundException e) {
                client.tell(FileClientMessage.newBuilder()
                        .setComplete(FileManagerProto.FileWriteComplete.newBuilder()
                                .setSeqId(0)
                                .setSuccess(false)
                                .setReason(e.toString())
                                .build())
                        .build(), server);
                ActorUnsafe.signal(server, new TaskStopSignal(taskId));
                return;
            }
            runTask(0, 0, stream);
        });
    }

    private void runTask(int seqId, long total, InputStream stream) {
        try {
            while (running) {
                byte[] buffer = new byte[8192];
                int read = stream.read(buffer);
                if (read == -1) {
                    client.tell(FileClientMessage.newBuilder()
                            .setComplete(FileManagerProto.FileWriteComplete.newBuilder()
                                    .setSeqId(seqId)
                                    .setSuccess(true)
                                    .setTotalLength(total)
                                    .build())
                            .build(), server);
                    ActorUnsafe.signal(server, new TaskStopSignal(taskId));
                    stream.close();
                    return;
                }
                try {
                    bucket.acquire(read);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                if (!running) {
                    throw new CancellationException();
                }
                client.tell(FileClientMessage.newBuilder()
                        .setEntry(FileManagerProto.FileWriteEntry.newBuilder()
                                .setSeqId(seqId)
                                .setData(UnsafeByteOperations.unsafeWrap(buffer, 0, read))
                                .build())
                        .build(), server);
                seqId++;
                total += read;
                long timeMs = (long) (bucket.calculateRequiredTime(8192) * 1000);
                if (timeMs != 0) {
                    int newSeqId = seqId;
                    long newTotal = total;
                    executor.schedule(() -> runTask(newSeqId, newTotal, stream),
                            timeMs, TimeUnit.MILLISECONDS);
                    return;
                }
            }
            assert !running;
            throw new CancellationException();
        } catch (Throwable e) {
            if (e instanceof CancellationException) {
                LOG.warn("{} cancelled", this, e);
            } else {
                LOG.error("{} error", this, e);
            }
            client.tell(FileClientMessage.newBuilder()
                    .setComplete(FileManagerProto.FileWriteComplete.newBuilder()
                            .setSeqId(seqId)
                            .setSuccess(false)
                            .setReason(e.toString())
                            .build())
                    .build(), server);
            ActorUnsafe.signal(server, new TaskStopSignal(taskId));
            try {
                stream.close();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    public void stop() {
        running = false;
    }

    @Override
    public String toString() {
        return "FileReadTask[" + file + ']';
    }

    public record TaskStopSignal(String taskId) implements Signal {
    }
}
