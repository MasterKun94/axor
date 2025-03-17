package io.masterkun.axor.testkit;

import io.masterkun.axor.api.ActorAddress;
import io.masterkun.axor.api.ActorRef;
import io.masterkun.axor.api.ActorRefRich;
import io.masterkun.axor.api.ActorSystem;
import io.masterkun.axor.runtime.MsgType;
import io.masterkun.axor.runtime.StreamDefinition;
import io.masterkun.axor.runtime.StreamManager;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class MessageBufferActorRef<T> extends ActorRefRich<T> {
    private final ActorAddress address;
    private final MsgType<T> msgType;
    private final BlockingQueue<MsgContext<T>> messages = new LinkedBlockingQueue<>();

    public MessageBufferActorRef(ActorSystem system, String name, MsgType<T> msgType) {
        this.address = ActorAddress.create(system.name(), system.publishAddress(), name);
        this.msgType = msgType;
    }

    public MessageBufferActorRef(ActorAddress address, MsgType<T> msgType) {
        this.address = address;
        this.msgType = msgType;
    }

    @Override
    public MsgType<? super T> msgType() {
        return msgType;
    }

    @Override
    public ActorAddress address() {
        return address;
    }

    @Override
    public void tell(T value, ActorRef<?> sender) {
        messages.add(new MsgContext<>(value, sender));
    }

    @Override
    public boolean isLocal() {
        return true;
    }

    public T pollMessage(long timeout, TimeUnit unit) throws InterruptedException {
        MsgContext<T> poll = poll(timeout, unit);
        return poll == null ? null : poll.msg;
    }

    public MsgContext<T> poll(long timeout, TimeUnit unit) throws InterruptedException {
        return messages.poll(timeout, unit);
    }

    public T pollMessage() {
        MsgContext<T> poll = poll();
        return poll == null ? null : poll.msg;
    }

    public MsgContext<T> poll() {
        return messages.poll();
    }

    public void clear() {
        messages.clear();
    }

    @Override
    public StreamDefinition<T> getDefinition() {
        throw new UnsupportedOperationException();
    }

    @Override
    public StreamManager<T> getStreamManager() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void cleanup() {

    }

    public record MsgContext<T>(T msg, ActorRef<?> sender) {
    }
}
