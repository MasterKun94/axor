package io.masterkun.axor.runtime;

import io.masterkun.axor.commons.RuntimeUtil;
import io.masterkun.axor.commons.collection.LongObjectHashMap;
import io.masterkun.axor.commons.collection.LongObjectMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Manages the lifecycle and communication of streams, including opening, closing, and handling messages.
 * This class is designed to work with a specific type of input and manages both incoming and outgoing streams.
 * It ensures that all operations are executed within the context of an event executor.
 *
 * @param <IN> the type of input this manager handles
 */
public class StreamManager<IN> implements Closeable {
    private static final Logger LOG = LoggerFactory.getLogger(StreamManager.class);
    private static final AtomicLong ID_GENERATOR = new AtomicLong();

    private final long id = ID_GENERATOR.getAndIncrement();
    private final StreamDefinition<IN> selfDefinition;
    private final StreamOutChannel<IN> streamOutChannel;
    private final EventDispatcher executor;
    private final Function<StreamDefinition<?>, MsgHandler<IN>> msgHandlerCreator;
    private Set<Runnable> closeListeners;
    private int streamId;
    private volatile boolean closed;
    private LongObjectMap<StreamCacheHolder> outCache;
    private int activeStreamsOut;
    private int activeStreamsIn;

    public StreamManager(StreamOutChannel<IN> streamOutChannel,
                         EventDispatcher executor,
                         Function<StreamDefinition<?>, MsgHandler<IN>> msgHandlerCreator) {
        this.streamOutChannel = streamOutChannel;
        this.selfDefinition = streamOutChannel.getSelfDefinition();
        this.executor = executor;
        this.msgHandlerCreator = msgHandlerCreator;
    }

    public int getActiveStreamsOut() {
        return activeStreamsOut;
    }

    public int getActiveStreamsIn() {
        return activeStreamsIn;
    }

    private StreamCacheHolder getOutCache(long id) {
        if (outCache == null) {
            outCache = new LongObjectHashMap<>();
        }
        StreamCacheHolder holder = outCache.get(id);
        if (holder == null) {
            outCache.put(id, holder = new StreamCacheHolder());
        }
        return holder;
    }

    public long id() {
        return id;
    }

    public void addCloseListener(Runnable listener) {
        if (executor.inExecutor()) {
            if (closeListeners == null) {
                closeListeners = new HashSet<>();
            }
            closeListeners.add(listener);
        } else {
            executor.execute(() -> addCloseListener(listener));
        }
    }

    public void removeCloseListener(Runnable listener) {
        if (executor.inExecutor()) {
            closeListeners.remove(listener);
        } else {
            executor.execute(() -> closeListeners.remove(listener));
        }
    }

    public EventDispatcher getExecutor() {
        return executor;
    }

    @SuppressWarnings("unchecked")
    public <OUT> StreamChannel.StreamObserver<OUT> getStreamOut(StreamManager<OUT> outManager) {
        if (closed) {
            throw new IllegalStateException("Stream manager closed");
        }
        if (!executor.inExecutor()) {
            throw new IllegalArgumentException("Not in executor");
        }
        StreamDefinition<OUT> outDefinition = outManager.selfDefinition;
        StreamCacheHolder cache = getOutCache(outManager.id());
        var cacheObserver = cache.getStreamOutObserver();
        if (cacheObserver != null) {
            return (StreamChannel.StreamObserver<OUT>) cacheObserver;
        }
        int streamId = this.streamId++;
        Runnable listener = new Runnable() {
            @Override
            public void run() {
                if (executor.inExecutor()) {
                    cache.completeAndClear();
                } else {
                    executor.execute(this);
                }
            }
        };

        outManager.addCloseListener(listener);
        var open = streamOutChannel.open(outDefinition, executor,
                new ManagerObserver(outDefinition, streamId, outManager.id()) {
                    @Override
                    public void onEnd(Status status) {
                        super.onEnd(status);
                        outManager.removeCloseListener(listener);
                    }
                });
        cache.putStreamOut(streamId, open);
        activeStreamsOut++;
        return open;
    }

    public StreamChannel.StreamObserver<IN> getStreamIn(StreamDefinition<?> outDefinition,
                                                        StreamChannel.Observer unaryOrObserver) {
        if (closed) {
            throw new IllegalStateException("Stream manager closed");
        }
        if (!executor.inExecutor()) {
            throw new IllegalArgumentException("Not in executor");
        }
        int streamId = this.streamId++;
        ManagerStreamObserver observer = new ManagerStreamObserver(outDefinition, streamId);
        var cache = getOutCache(observer.id());
        cache.putStreamIn(streamId, unaryOrObserver);
        activeStreamsIn++;
        return observer;
    }

    @Override
    public void close() {
        if (executor.inExecutor()) {
            closed = true;
            if (closeListeners != null) {
                for (Runnable listener : closeListeners) {
                    listener.run();
                }
            }
            if (outCache != null) {
                for (var cache : outCache.values()) {
                    cache.completeAndClear();
                }
                outCache.clear();
            }
        } else {
            executor.execute(this::close);
        }
    }

    private void onEnd(boolean streamIn, StreamAddress from, StreamAddress to, long toId,
                       Status status, int streamId) {
        var code = status.codeStatus();
        String prefix = streamIn ? "StramIn" : "StramOut";
        if (code.success) {
            LOG.debug("{}[{} -> {}] closed with status {}", prefix, from, to, code);
        } else {
            if (status.cause() != null) {
                if (LOG.isDebugEnabled()) {
                    LOG.warn("{}[{} -> {}] closed with status {}",
                            prefix, from, to, code, status.cause());
                } else {
                    LOG.warn("{}[{} -> {}] closed with status {}, error: {}",
                            prefix, from, to, code, RuntimeUtil.toSimpleString(status.cause()));
                }
            } else {
                LOG.warn("{}[{} -> {}] closed with status {}", prefix, from, to, code);
            }
        }
        var cache = outCache.get(toId);
        if (cache != null) {
            var removed = streamIn ?
                    cache.removeStreamIn(streamId) :
                    cache.removeStreamOut(streamId);
            if (removed != null) {
                if (streamIn) {
                    activeStreamsIn--;
                } else {
                    activeStreamsOut--;
                }
                LOG.debug("{}[{}] closed", prefix, streamId);
                removed.onEnd(StatusCode.COMPLETE.toStatus());
            }
            if (cache.isEmpty()) {
                outCache.remove(toId, cache);
            }
        }
    }

    public static abstract class MsgHandler<IN> {
        private final StreamDefinition<?> from;

        protected MsgHandler(StreamDefinition<?> from) {
            this.from = from;
        }

        public StreamDefinition<?> getFrom() {
            return from;
        }

        public abstract void handle(IN msg);

        public abstract long id();
    }

    private class ManagerObserver implements StreamChannel.Observer {
        private final int streamId;
        private final StreamDefinition<?> outDefinition;
        private final long outId;

        private ManagerObserver(StreamDefinition<?> outDefinition, int streamId, long outId) {
            this.outDefinition = outDefinition;
            this.streamId = streamId;
            this.outId = outId;
        }

        @Override
        public void onEnd(Status status) {
            if (!executor.inExecutor()) {
                executor.execute(() -> onEnd(status));
                return;
            }
            var from = selfDefinition.address();
            var to = outDefinition.address();
            StreamManager.this.onEnd(false, from, to, outId, status, streamId);
        }
    }

    private final class ManagerStreamObserver implements StreamChannel.StreamObserver<IN> {
        private final MsgHandler<IN> msgHandler;
        private final int streamId;
        private final StreamDefinition<?> outDefinition;

        private ManagerStreamObserver(StreamDefinition<?> outDefinition, int streamId) {
            this.outDefinition = outDefinition;
            this.streamId = streamId;
            this.msgHandler = msgHandlerCreator.apply(outDefinition);
        }

        public long id() {
            return msgHandler.id();
        }

        @Override
        public void onNext(IN in) {
            if (executor.inExecutor()) {
                msgHandler.handle(in);
            } else {
                executor.execute(() -> msgHandler.handle(in));
            }
        }

        @Override
        public void onEnd(Status status) {
            if (!executor.inExecutor()) {
                executor.execute(() -> onEnd(status));
                return;
            }
            var from = outDefinition.address();
            var to = selfDefinition.address();
            StreamManager.this.onEnd(true, from, to, id, status, streamId);
        }
    }
}

class StreamCacheHolder {
    private int streamOutId;
    private StreamChannel.StreamObserver<?> streamOutObserver;
    private int streamInId;
    private StreamChannel.Observer streamInObserver;

    public void putStreamOut(int streamOutId, StreamChannel.StreamObserver<?> streamOutObserver) {
        this.streamOutId = streamOutId;
        this.streamOutObserver = streamOutObserver;
    }

    public StreamChannel.StreamObserver<?> getStreamOutObserver() {
        return streamOutObserver;
    }

    public StreamChannel.StreamObserver<?> removeStreamOut(int id) {
        if (streamOutId == id) {
            StreamChannel.StreamObserver<?> observer = streamOutObserver;
            streamOutObserver = null;
            return observer;
        }
        return null;
    }

    public void putStreamIn(int streamInId, StreamChannel.Observer streamInObserver) {
        this.streamInId = streamInId;
        this.streamInObserver = streamInObserver;
    }

    public StreamChannel.Observer getStreamInObserver() {
        return streamInObserver;
    }

    public StreamChannel.Observer removeStreamIn(int id) {
        if (streamInId == id) {
            StreamChannel.Observer observer = streamInObserver;
            streamInObserver = null;
            return observer;
        }
        return null;
    }

    public void completeAndClear() {
        if (streamOutObserver != null) {
            streamOutObserver.onEnd(StatusCode.COMPLETE.toStatus());
            streamOutObserver = null;
        }
        if (streamInObserver != null) {
            streamInObserver.onEnd(StatusCode.COMPLETE.toStatus());
            streamInObserver = null;
        }
    }

    public boolean isEmpty() {
        return streamOutObserver == null && streamInObserver == null;
    }
}
