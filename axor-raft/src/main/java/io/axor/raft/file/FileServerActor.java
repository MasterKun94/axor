package io.axor.raft.file;

import io.axor.api.Actor;
import io.axor.api.ActorContext;
import io.axor.api.MessageUtils;
import io.axor.raft.proto.FileManagerProto;
import io.axor.raft.proto.FileManagerProto.FileClientMessage;
import io.axor.raft.proto.FileManagerProto.FileServerMessage;
import io.axor.runtime.MsgType;
import io.axor.runtime.Signal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

public class FileServerActor extends Actor<FileServerMessage> {
    private static final Logger LOG = LoggerFactory.getLogger(FileServerActor.class);

    private final Map<String, FileReadTask> tasks = new HashMap<>();
    private final ScheduledExecutorService executor;

    public FileServerActor(ActorContext<FileServerMessage> context,
                           ScheduledExecutorService executor) {
        super(context);
        this.executor = executor;
    }

    @Override
    public void onReceive(FileServerMessage msg) {
        LOG.info("Receive {}", MessageUtils.loggable(msg));
        switch (msg.getMsgCase()) {
            case START -> onFileReadStart(msg.getStart());
            case STOP -> onFileReadStop(msg.getStop());
            default -> throw new IllegalArgumentException("Unsupported message type: " +
                                                          msg.getMsgCase());
        }
    }

    private void onFileReadStart(FileManagerProto.FileReadStart start) {
        String taskId = start.getReqId();
        if (tasks.containsKey(taskId)) {
            sender(FileClientMessage.class).tell(FileClientMessage.newBuilder()
                    .setComplete(FileManagerProto.FileWriteComplete.newBuilder()
                            .setReason("reqId " + taskId + " already exists")
                            .setSuccess(false)
                            .build())
                    .build());
            return;
        }
        TokenBucket bucket = new TokenBucket(start.getBytesPerSec() / 2, start.getBytesPerSec());
        FileReadTask task = new FileReadTask(taskId, executor, bucket,
                new File(start.getPath()), self(), sender(FileClientMessage.class));
        tasks.put(taskId, task);
        task.run();
    }

    private void onFileReadStop(FileManagerProto.FileReadStop stop) {
        FileReadTask task = tasks.get(stop.getReqId());
        if (task != null) {
            task.stop();
        }
    }

    @Override
    public void onSignal(Signal signal) {
        if (signal instanceof FileReadTask.TaskStopSignal(var taskId)) {
            FileReadTask task = tasks.remove(taskId);
            if (task != null) {
                task.stop();
            }
        }
    }

    @Override
    public MsgType<FileServerMessage> msgType() {
        return MsgType.of(FileServerMessage.class);
    }
}
