package io.masterkun.axor.api.impl;

import io.masterkun.axor.api.ActorAddress;
import io.masterkun.axor.api.ActorRef;
import io.masterkun.axor.runtime.HasMeter;
import io.masterkun.axor.runtime.MsgType;
import io.masterkun.axor.runtime.StreamManager;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class MapActorRefCache implements ActorRefCache, HasMeter {
    private static final Logger LOG = LoggerFactory.getLogger(MapActorRefCache.class);
    private static final ReferenceQueue<RemoteActorRef<?>> referenceQueue = new ReferenceQueue<>();

    static {
        Thread cleanupThread = new Thread(new CleanupTask());
        cleanupThread.setDaemon(true);
        cleanupThread.setName("ActorRefCacheCleanupTask");
        cleanupThread.start();
    }

    private final Map<ActorAddress, Reference<RemoteActorRef<?>>> cache;
    private final Factory factory;
    private final boolean softReference;

    public MapActorRefCache(Factory factory, boolean softReference) {
        this.softReference = softReference;
        this.cache = new ConcurrentHashMap<>();
        this.factory = factory;
    }

    @Override
    public ActorRef<?> get(ActorAddress address) {
        var reference = cache.get(address);
        if (reference == null) {
            throw new IllegalStateException("Cannot find actor ref for address " + address);
        }
        ActorRef<?> actorRef = reference.get();
        if (actorRef == null) {
            cache.remove(address, reference);
            throw new IllegalStateException("Cannot find actor ref for address " + address);
        }
        return actorRef;
    }

    private Reference<RemoteActorRef<?>> createReference(ActorAddress address, MsgType<?> msgType) {
        var ret = factory.create(address, msgType);
        LOG.debug("Add {} to cache", ret);
        var manager = ret.getStreamManager();
        if (softReference) {
            return new SoftReferenceRich(ret, referenceQueue, manager, cache);
        } else {
            return new WeakReferenceRich(ret, referenceQueue, manager, cache);
        }
    }

    @Override
    public <T> ActorRef<T> getOrCreate(ActorAddress address, MsgType<T> msgType) {
        ActorRef<?> actorRef;
        do {
            actorRef = cache.compute(address, (k, v) -> {
                if (v == null || v.get() == null) {
                    return createReference(k, msgType);
                }
                return v;
            }).get();
        } while (actorRef == null);
        return actorRef.cast(msgType);
    }

    @Override
    public void remove(ActorRef<?> actorRef) {
        cache.compute(actorRef.address(), (k, v) -> {
            if (v == null) {
                return null;
            }
            ActorRef<?> ref = v.get();
            if (ref == null) {
                return null;
            }
            if (ref == actorRef) {
                LOG.debug("Removing {} from cache", ref);
                return null;
            }
            return v;
        });
    }

    @Override
    public Stream<? extends RemoteActorRef<?>> getAll() {
        return cache.values().stream()
                .map(Reference::get)
                .filter(Objects::nonNull);
    }

    @Override
    public void register(MeterRegistry registry, String... tags) {
        Gauge.builder("axor.active.streams.out", cache,
                        m -> m.values().stream()
                                .mapToInt(e -> {
                                    RemoteActorRef<?> get = e.get();
                                    return get == null ? 0 : get.getStreamManager().getActiveStreamsOut();
                                })
                                .sum())
                .description("Number of active streams out")
                .tags(tags)
                .register(registry);
    }

    private interface ReferenceRich {

        StreamManager<?> streamManager();

        ActorAddress address();

        Map<ActorAddress, Reference<RemoteActorRef<?>>> cache();
    }

    private static class CleanupTask implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    Reference<? extends RemoteActorRef<?>> reference = referenceQueue.remove();
                    if (reference instanceof ReferenceRich cleanup) {
                        LOG.debug("Cleaning up {}", cleanup.address());
                        cleanup.streamManager().close();
                        cleanup.cache().remove(cleanup.address(), reference);
                    }
                } catch (InterruptedException e) {
                    LOG.debug("Cleanup task interrupted", e);
                } catch (Throwable e) {
                    LOG.error("Unexpected error while cleanup", e);
                }
            }
        }
    }

    private static class WeakReferenceRich extends WeakReference<RemoteActorRef<?>> implements ReferenceRich {
        private final StreamManager<?> streamManager;
        private final ActorAddress address;
        private final Map<ActorAddress, Reference<RemoteActorRef<?>>> cache;

        public WeakReferenceRich(RemoteActorRef<?> referent,
                                 ReferenceQueue<RemoteActorRef<?>> q,
                                 StreamManager<?> streamManager,
                                 Map<ActorAddress, Reference<RemoteActorRef<?>>> cache) {
            super(referent, q);
            this.streamManager = streamManager;
            this.address = referent.address();
            this.cache = cache;
        }

        @Override
        public StreamManager<?> streamManager() {
            return streamManager;
        }

        @Override
        public ActorAddress address() {
            return address;
        }

        @Override
        public Map<ActorAddress, Reference<RemoteActorRef<?>>> cache() {
            return cache;
        }
    }

    private static class SoftReferenceRich extends WeakReference<RemoteActorRef<?>> implements ReferenceRich {
        private final StreamManager<?> streamManager;
        private final ActorAddress address;
        private final Map<ActorAddress, Reference<RemoteActorRef<?>>> cache;

        public SoftReferenceRich(RemoteActorRef<?> referent,
                                 ReferenceQueue<RemoteActorRef<?>> q,
                                 StreamManager<?> streamManager,
                                 Map<ActorAddress, Reference<RemoteActorRef<?>>> cache) {
            super(referent, q);
            this.address = referent.address();
            this.streamManager = streamManager;
            this.cache = cache;
        }

        @Override
        public StreamManager<?> streamManager() {
            return streamManager;
        }

        @Override
        public ActorAddress address() {
            return address;
        }

        @Override
        public Map<ActorAddress, Reference<RemoteActorRef<?>>> cache() {
            return cache;
        }
    }
}
