package io.axor.raft.file;

import com.google.protobuf.ByteString;
import io.axor.api.Actor;
import io.axor.api.ActorContext;
import io.axor.api.ActorRef;
import io.axor.api.FailureStrategy;
import io.axor.raft.proto.FileManagerProto;
import io.axor.raft.proto.FileManagerProto.FileClientMessage;
import io.axor.raft.proto.FileManagerProto.FileServerMessage;
import io.axor.raft.proto.FileManagerProto.FileWriteComplete;
import io.axor.raft.proto.FileManagerProto.FileWriteEntry;
import io.axor.runtime.MsgType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.Flow;

public class FileClientActor extends Actor<FileClientMessage> {
    private static final Logger LOG = LoggerFactory.getLogger(FileClientActor.class);

    private final ActorRef<FileServerMessage> server;
    private final UUID id = UUID.randomUUID();
    private final long bytesPerSec;
    private final String path;
    private Flow.Subscriber<ByteString> subscriber;
    private long nextSeqId = 0;
    private long totalLen = 0;

    protected FileClientActor(ActorContext<FileClientMessage> context,
                              ActorRef<FileServerMessage> server,
                              long bytesPerSec,
                              String path,
                              Flow.Subscriber<ByteString> subscriber) {
        super(context);
        this.server = server;
        this.bytesPerSec = bytesPerSec;
        this.path = path;
        this.subscriber = subscriber;
    }

    @Override
    public void onStart() {
        server.tell(FileServerMessage.newBuilder()
                .setStart(FileManagerProto.FileReadStart.newBuilder()
                        .setReqId(id.toString())
                        .setBytesPerSec(bytesPerSec)
                        .setPath(path))
                .build(), self());

        try {
            subscriber.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {
                    // TODO
                }

                @Override
                public void cancel() {
                    server.tell(FileServerMessage.newBuilder()
                            .setStop(FileManagerProto.FileReadStop.newBuilder()
                                    .setReqId(id.toString()))
                            .build(), self());
                }
            });
        } catch (Throwable e) {
            LOG.error("Unexpected error while open handler", e);
            subscriber.onError(e);
            subscriber = null;
            context().stop();
        }
    }

    private void checkId(long id) {
        if (nextSeqId != id) {
            throw new IllegalArgumentException("seqId mismatch");
        }
        nextSeqId++;
    }

    @Override
    public void onReceive(FileClientMessage msg) {
        try {
            switch (msg.getMsgCase()) {
                case ENTRY -> {
                    FileWriteEntry entry = msg.getEntry();
                    checkId(entry.getSeqId());
                    totalLen += entry.getData().size();
                    subscriber.onNext(entry.getData());
                }
                case COMPLETE -> {
                    FileWriteComplete complete = msg.getComplete();
                    checkId(complete.getSeqId());
                    if (totalLen != complete.getTotalLength()) {
                        throw new IllegalArgumentException("totalLen mismatch");
                    }
                    if (complete.getSuccess()) {
                        subscriber.onComplete();
                        subscriber = null;
                    } else {
                        subscriber.onError(new IOException(complete.getReason()));
                        subscriber = null;
                    }
                    context().stop();
                }
            }
        } catch (Throwable e) {
            if (subscriber != null) {
                subscriber.onError(e);
                subscriber = null;
            }
            LOG.error("Unexpected error while handle msg", e);
            context().stop();
        }
    }

    @Override
    public void preStop() {
        server.tell(FileServerMessage.newBuilder()
                .setStop(FileManagerProto.FileReadStop.newBuilder()
                        .setReqId(id.toString()))
                .build(), self());
        if (subscriber != null) {
            subscriber.onError(new IOException("client stopped"));
            subscriber = null;
        }
    }

    @Override
    public FailureStrategy failureStrategy(Throwable throwable) {
        return FailureStrategy.STOP;
    }

    @Override
    public MsgType<FileClientMessage> msgType() {
        return MsgType.of(FileClientMessage.class);
    }
}
