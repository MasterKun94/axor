package io.masterkun.axor.api.impl;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.masterkun.axor.api.Actor;
import io.masterkun.axor.api.ActorAddress;
import io.masterkun.axor.api.ActorCreator;
import io.masterkun.axor.api.ActorRef;
import io.masterkun.axor.api.ActorRefRich;
import io.masterkun.axor.api.ActorSystem;
import io.masterkun.axor.api.ActorSystemSerdeInitializer;
import io.masterkun.axor.api.Address;
import io.masterkun.axor.api.DeadLetter;
import io.masterkun.axor.api.Pubsub;
import io.masterkun.axor.api.SystemEvent;
import io.masterkun.axor.commons.config.ConfigMapper;
import io.masterkun.axor.commons.task.DependencyTask;
import io.masterkun.axor.commons.task.DependencyTaskRegistry;
import io.masterkun.axor.commons.task.DependencyTaskRegistryRunner;
import io.masterkun.axor.config.ActorConfig;
import io.masterkun.axor.exception.ActorNotFoundException;
import io.masterkun.axor.exception.IllegalMsgTypeException;
import io.masterkun.axor.runtime.EventDispatcher;
import io.masterkun.axor.runtime.EventDispatcherGroup;
import io.masterkun.axor.runtime.EventDispatcherGroupBuilderProvider;
import io.masterkun.axor.runtime.HasMeter;
import io.masterkun.axor.runtime.MsgType;
import io.masterkun.axor.runtime.Registry;
import io.masterkun.axor.runtime.Serde;
import io.masterkun.axor.runtime.SerdeRegistry;
import io.masterkun.axor.runtime.StreamDefinition;
import io.masterkun.axor.runtime.StreamServer;
import io.masterkun.axor.runtime.StreamServerBuilderProvider;
import io.masterkun.stateeasy.concurrent.EventPromise;
import io.masterkun.stateeasy.concurrent.EventStage;
import io.masterkun.stateeasy.concurrent.Try;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class ActorSystemImpl implements ActorSystem, HasMeter {
    private static final Map<CacheKey, ActorSystemImpl> SYSTEM_CACHE = new ConcurrentHashMap<>();
    private final Logger LOG = LoggerFactory.getLogger(ActorSystemImpl.class);
    private final String name;
    private final StreamServer streamServer;
    private final Address publishAddress;
    private final EventDispatcherGroup eventExecutorGroup;
    private final Map<String, ActorRef<?>> localActorCache = new ConcurrentHashMap<>();
    private final ActorRefCache remoteActorCache =
            new MapActorRefCache(this::createRemoteActorRef, false);
    private final ActorRef<Object> noSenderActor;
    private final ActorConfig actorConfig;
    private final Config config;
    private final Pubsub<DeadLetter> deadLetterPubsub;
    private final Pubsub<SystemEvent> systemEventPubsub;
    private final DependencyTaskRegistryRunner shutdownHooks =
            new DependencyTaskRegistryRunner(true);
    private volatile boolean closed = false;

    public ActorSystemImpl(String name,
                           StreamServer streamServer,
                           EventDispatcherGroup eventExecutorGroup,
                           Config config) {
        if (streamServer == null) {
            var deadLetterHandlerFactory = new ActorDeadLetterHandlerFactory();
            streamServer = Registry.getByPriority(StreamServerBuilderProvider.class)
                    .createFromRootConfig(config)
                    .system(name)
                    .serdeRegistry(new SerdeRegistry(config))
                    .deadLetterHandler(deadLetterHandlerFactory)
                    .build();
            try {
                streamServer.start();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            deadLetterHandlerFactory.init(this);
        } else {
            config = ConfigFactory.parseString("axor.network.bind.port=" + streamServer.bindPort())
                    .withFallback(config);
        }

        if (eventExecutorGroup == null) {
            eventExecutorGroup = Registry.getByPriority(EventDispatcherGroupBuilderProvider.class)
                    .createFromRootConfig(config).build();
        }
        this.actorConfig = ConfigMapper.map(config.getConfig("axor"), ActorConfig.class);
        this.config = config;
        Address publishAddress = actorConfig.network().publishAddress();
        if (SYSTEM_CACHE.putIfAbsent(new CacheKey(name, publishAddress), this) != null) {
            throw new IllegalArgumentException("ActorSystem key already exists: " +
                                               ActorAddress.create(name, publishAddress, ""));
        }
        this.name = name;
        this.streamServer = streamServer;
        this.publishAddress = publishAddress;
        this.eventExecutorGroup = eventExecutorGroup;
        this.shutdownHooks.register(new RootShutdownTask());
        this.deadLetterPubsub = Pubsub.get("sys/DeadLetter", MsgType.of(DeadLetter.class),
                false, this);
        this.systemEventPubsub = Pubsub.get("sys/SystemEvent", MsgType.of(SystemEvent.class),
                false, this);
        var address = ActorAddress.create(this.name, publishAddress, NoSenderActorRef.ACTOR_NAME);
        var executor = eventExecutorGroup.nextDispatcher();
        var serde = NoSenderActorRef.SERDE;
        var def = new StreamDefinition<>(address.streamAddress(), serde);
        var channel = streamServer.get(def, executor);
        noSenderActor = new NoSenderActorRef(address, channel, executor);
        SerdeRegistry registry = streamServer.serdeRegistry();
        var initializers = Registry.listAvailable(ActorSystemSerdeInitializer.class)
                .stream()
                .sorted(Comparator.comparing(ActorSystemSerdeInitializer::priority))
                .toList();
        for (ActorSystemSerdeInitializer<?> initializer : initializers) {
            if (initializer.maybeInitialize(this, registry)) {
                LOG.info("{} is initialized", initializer.getClass().getSimpleName());
            }
        }
        if (actorConfig.logger().logDeadLetters()) {
            deadLetterPubsub.subscribe(start(c -> new Actor<>(c) {
                @Override
                public void onReceive(DeadLetter deadLetter) {
                    LOG.warn("Receive dead letter {}", deadLetter);
                }

                @Override
                public MsgType<DeadLetter> msgType() {
                    return MsgType.of(DeadLetter.class);
                }
            }, "sys/DeadLetterListener"));
        }
    }

    public static boolean hasMultipalInstance() {
        return SYSTEM_CACHE.size() > 1;
    }

    private <T> Serde<T> getSerde(MsgType<T> type) {
        return streamServer.serdeRegistry().create(type);
    }

    void replaceCache(ActorRef<?> replace) {
        ActorRef<?> get = localActorCache.get(replace.address().name());
        if (get == null) {
            throw new IllegalArgumentException("actor not found");
        }
        if (!get.address().equals(replace.address())) {
            throw new IllegalArgumentException("address not same");
        }
        localActorCache.put(replace.address().name(), get);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public Address publishAddress() {
        return publishAddress;
    }

    @Override
    public Logger getLogger() {
        return LOG;
    }

    @Override
    public boolean isLocal(ActorRef<?> actor) {
        return actor.isLocal() &&
               actor.address().address().equals(publishAddress) &&
               actor.address().system().equals(name);
    }

    @Override
    public void systemFailure(Throwable cause) {
        LOG.error("System failure", cause);
        System.exit(1);
    }

    @Override
    public Pubsub<DeadLetter> deadLetters() {
        return deadLetterPubsub;
    }

    @Override
    public Pubsub<SystemEvent> systemEvents() {
        return systemEventPubsub;
    }

    private boolean isLocalAddress(ActorAddress actorAddress) {
        if (!name.equals(actorAddress.system())) {
            return false;
        }
        Address address = actorAddress.address();
        if (address.equals(publishAddress)) {
            return true;
        } else if (address.port() == publishAddress.port()) {
            InetAddress parsed;
            try {
                parsed = InetAddress.getByName(address.host());
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }
            return parsed.isAnyLocalAddress() || parsed.isLoopbackAddress();
        } else {
            return false;
        }
    }

    private void checkClosed() {
        if (closed) {
            throw new IllegalStateException("ActorSystem has been closed");
        }
    }

    @Override
    public <T> ActorRef<T> start(ActorCreator<T> creator, String name, EventDispatcher dispatcher) {
        return start(creator, name, dispatcher, false);
    }

    @Override
    public <T> ActorRef<T> getOrStart(ActorCreator<T> creator, String name,
                                      EventDispatcher dispatcher) {
        return start(creator, name, dispatcher, true);
    }

    @SuppressWarnings("unchecked")
    public <T> ActorRef<T> start(ActorCreator<T> creator, String name, EventDispatcher dispatcher
            , boolean cacheGet) {
        checkClosed();
        ActorRef<?> ret = localActorCache.compute(name, (k, v) -> {
            if (v != null) {
                if (cacheGet) {
                    return v;
                } else {
                    throw new IllegalArgumentException("Actor already started: " + name);
                }
            }
            var address = ActorAddress.create(this.name, publishAddress, k);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Creating new actor ref: {}", address);
            }
            return new LocalActorRef<>(address, this, dispatcher, creator, actorConfig);
        });
        return (ActorRef<T>) ret;
    }

    @Override
    public EventStage<Void> stop(ActorRef<?> actor) {
        if (actor instanceof LocalActorRef<?> localActorRef) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Stopping actor {}", actor);
            }
            localActorCache.remove(localActorRef.address().name());
            EventPromise<Void> promise = localActorRef.getActor()
                    .context().dispatcher().newPromise();
            localActorRef.stop(promise);
            return promise;
        } else {
            var ex = new IllegalArgumentException("ActorRef is not a LocalActorRef");
            return EventStage.failed(ex, getDispatcherGroup().nextDispatcher());
        }
    }

    @Override
    public ActorRef<?> get(ActorAddress address) throws ActorNotFoundException {
        checkClosed();
        if (isLocalAddress(address)) {
            ActorRef<?> actorRef = localActorCache.get(address.name());
            if (actorRef == null) {
                throw new ActorNotFoundException(address);
            }
            return actorRef;
        } else {
            return remoteActorCache.get(address);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> ActorRef<T> checkType(ActorRef<?> actorRef, MsgType<T> msgType) throws IllegalMsgTypeException {
        if (!actorRef.msgType().equals(msgType)) {
            throw new IllegalMsgTypeException(actorRef.msgType(), msgType);
        }
        return (ActorRef<T>) actorRef;
    }

    private <T> RemoteActorRef<T> createRemoteActorRef(ActorAddress address, MsgType<T> msgType) {
        var executor = EventDispatcher.current();
        if (executor == null) {
            executor = eventExecutorGroup.nextDispatcher();
        }
        var definition = new StreamDefinition<>(address.streamAddress(), getSerde(msgType));
        return RemoteActorRef.create(address,
                streamServer.serdeRegistry().create(msgType),
                executor,
                streamServer.get(definition, executor),
                this);
    }

    @Override
    public <T> ActorRef<T> get(ActorAddress address, MsgType<T> msgType) throws ActorNotFoundException, IllegalMsgTypeException {
        checkClosed();
        if (isLocalAddress(address)) {
            ActorRef<?> actorRef = localActorCache.get(address.name());
            if (actorRef == null) {
                throw new ActorNotFoundException(address);
            }
            return checkType(actorRef, msgType);
        } else {
            return remoteActorCache.getOrCreate(address, msgType);
        }
    }

    @Override
    public <T> ActorRef<T> noSender() {
        checkClosed();
        return ((ActorRefRich<?>) noSenderActor).unsafeCast();
    }

    @Override
    public SerdeRegistry getSerdeRegistry() {
        return streamServer.serdeRegistry();
    }

    @Override
    public StreamServer getStreamServer() {
        return streamServer;
    }

    @Override
    public EventDispatcherGroup getDispatcherGroup() {
        return eventExecutorGroup;
    }

    @Override
    public CompletableFuture<Void> shutdownAsync() {
        return shutdownHooks.run();
    }

    @Override
    public DependencyTaskRegistry shutdownHooks() {
        return shutdownHooks;
    }

    @Override
    public void register(MeterRegistry registry, String... tags) {
        Gauge.builder("axor.active.streams.in", remoteActorCache, c -> c.getAll()
                        .mapToInt(a -> a.getStreamManager().getActiveStreamsIn())
                        .sum())
                .description("Number of active streams inbound")
                .tags(tags)
                .tag("actor_type", "remote")
                .register(registry);
        Gauge.builder("axor.active.streams.out", remoteActorCache, c -> c.getAll()
                        .mapToInt(a -> a.getStreamManager().getActiveStreamsOut())
                        .sum())
                .description("Number of active streams outbound")
                .tags(tags)
                .tag("actor_type", "remote")
                .register(registry);
        Gauge.builder("axor.active.streams.in", localActorCache, c -> c.values().stream()
                        .mapToInt(a -> ((ActorRefRich<?>) a).getStreamManager().getActiveStreamsIn())
                        .sum())
                .description("Number of active streams inbound")
                .tags(tags)
                .tag("actor_type", "local")
                .register(registry);
        Gauge.builder("axor.active.streams.out", localActorCache, c -> c.values().stream()
                        .mapToInt(a -> ((ActorRefRich<?>) a).getStreamManager().getActiveStreamsOut())
                        .sum())
                .description("Number of active streams outbound")
                .tags(tags)
                .tag("actor_type", "local")
                .register(registry);
        if (eventExecutorGroup instanceof HasMeter hasMetrics) {
            hasMetrics.register(registry, tags);
        }
        if (streamServer instanceof HasMeter hasMetrics) {
            hasMetrics.register(registry, tags);
        }
    }

    private record CacheKey(String system, String host, int port) {
        CacheKey(String system, Address address) {
            this(system, address.host(), address.port());
        }
    }

    private class RootShutdownTask extends DependencyTask {

        RootShutdownTask() {
            super("root");
        }

        @Override
        public CompletableFuture<Void> run() {
            EventStage<Void> stage = EventStage.succeed(null, eventExecutorGroup.nextDispatcher());
            for (ActorRef<?> actor : localActorCache.values()) {
                if (stage == null) {
                    stage = stop(actor);
                } else {
                    stage = stage.flatmap(v -> stop(actor));
                }
            }
            return stage
                    .transform(t -> {
                        if (t.isFailure()) {
                            LOG.error("Stop actor failed", t.cause());
                        }
                        closed = true;
                        return Try.success(null);
                    })
                    .toCompletableFuture()
                    .thenCompose(v -> {
                        SYSTEM_CACHE.remove(new CacheKey(name, publishAddress));
                        CompletableFuture<Void> future = streamServer.shutdownAsync();
                        try {
                            remoteActorCache.cleanup();
                        } catch (Throwable e) {
                            LOG.error("Cleanup RemoteActorRefCache failed", e);
                        }
                        return future;
                    })
                    .exceptionally(e -> {
                        LOG.error("Shutdown StreamServer failed", e);
                        return null;
                    })
                    .thenCompose(v -> eventExecutorGroup.shutdownAsync())
                    .exceptionally(e -> {
                        LOG.error("Shutdown DispatcherGroup failed", e);
                        return null;
                    });
        }
    }
}
