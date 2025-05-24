package io.axor.raft;

import io.axor.api.ActorAddress;
import io.axor.api.ActorRef;
import io.axor.api.ActorSystem;
import io.axor.commons.concurrent.EventExecutor;
import io.axor.commons.concurrent.EventPromise;
import io.axor.commons.concurrent.EventStage;
import io.axor.commons.concurrent.Failure;
import io.axor.commons.concurrent.Success;
import io.axor.commons.concurrent.Try;
import io.axor.exception.ActorNotFoundException;
import io.axor.exception.IllegalMsgTypeException;
import io.axor.raft.file.FileConfig;
import io.axor.raft.file.FileService;
import io.axor.raft.logging.SnapshotStore;
import io.axor.raft.proto.FileManagerProto.FileServerMessage;
import io.axor.raft.proto.PeerProto.InstallSnapshot;
import io.axor.raft.proto.PeerProto.Snapshot;

import java.io.File;
import java.nio.file.Path;

import static io.axor.runtime.stream.grpc.StreamUtils.protoToActorAddress;

public class RaftUtils {
    private final ActorSystem system;
    private final FileConfig config;
    private final SnapshotStore snapshotStore;

    public RaftUtils(ActorSystem system, FileConfig config, SnapshotStore snapshotStore) {
        this.system = system;
        this.config = config;
        this.snapshotStore = snapshotStore;
    }

    private File snapshotFile(long id, String fileName) {
        String basePath = config.baseDir().getAbsolutePath();
        return Path.of(basePath, Long.toString(id), new File(fileName).getName()).toFile();
    }

    public EventStage<Snapshot> syncInstallSnapshot(InstallSnapshot installSnapshot,
                                                    EventExecutor executor) {
        Snapshot snapshot = installSnapshot.getSnapshot();
        Snapshot.Builder builder = Snapshot.newBuilder()
                .setId(snapshot.getId())
                .setData(snapshot.getData());
        ActorAddress address = protoToActorAddress(installSnapshot.getFileServerAddress());
        ActorRef<FileServerMessage> serverRef;
        try {
            serverRef = system.get(address, FileServerMessage.class);
        } catch (ActorNotFoundException | IllegalMsgTypeException e) {
            throw new RuntimeException(e);
        }
        FileService fileService = new FileService(system, serverRef, config);
        EventStage<Void> stage = EventStage.succeed(null, executor);
        for (String file : snapshot.getFilesList()) {
            File targetFile = snapshotFile(snapshot.getId(), file);
            stage = stage.flatmap(v -> {
                EventPromise<Void> newPromise = executor.newPromise();
                return fileService.copyTo(file, targetFile, newPromise);
            });
            builder.addFiles(targetFile.toString());
        }
        Snapshot ret = builder.build();
        return stage.transform(t -> {
            switch (t) {
                case Success<Void> ignore -> {
                    try {
                        snapshotStore.install(ret);
                        return Try.success(ret);
                    } catch (RaftException e) {
                        return Try.failure(e);
                    }
                }
                case Failure<Void>(var cause) -> {
                    return Try.failure(cause);
                }
                case null, default -> throw new IllegalStateException("Unhandled exception");
            }
        });
    }
}
