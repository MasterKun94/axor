package io.axor.raft.file;

import com.google.protobuf.ByteString;
import io.axor.api.ActorRef;
import io.axor.api.ActorSystem;
import io.axor.commons.concurrent.EventPromise;
import io.axor.commons.concurrent.EventStage;
import io.axor.raft.proto.FileManagerProto;
import io.axor.raft.proto.FileManagerProto.FileServerMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

public class FileService {
    private static final Logger LOG = LoggerFactory.getLogger(FileService.class);
    private static final AtomicInteger ADDER = new AtomicInteger();
    private final ActorSystem system;
    private final ActorRef<FileServerMessage> server;
    private final long bytesPerSec;

    public FileService(ActorSystem system, ActorRef<FileServerMessage> server, FileConfig config) {
        this.system = system;
        this.server = server;
        this.bytesPerSec = config.fileTransferBytesPerSec().toBytes();
    }

    public void subscribe(String filePath, Flow.Subscriber<ByteString> subscriber) {
        system.<FileManagerProto.FileClientMessage>start(
                c -> new FileClientActor(c, server, bytesPerSec, filePath, subscriber),
                "fileClient/" + Path.of(filePath).getFileName() + "-" + ADDER.getAndIncrement());
    }

    public CompletableFuture<Void> copyTo(String sourcePath, File targetFile) {
        FileWriteSubscriber subscriber = new FileWriteSubscriber(targetFile);
        subscribe(sourcePath, subscriber);
        return subscriber.getFuture();
    }

    public EventStage<Void> copyTo(String sourcePath, File targetFile, EventPromise<Void> promise) {
        CompletableFuture<Void> future = copyTo(sourcePath, targetFile);
        future.whenComplete((v, e) -> {
            if (e != null) {
                promise.failure(e);
            } else {
                promise.success(v);
            }
        });
        return promise;
    }
}
