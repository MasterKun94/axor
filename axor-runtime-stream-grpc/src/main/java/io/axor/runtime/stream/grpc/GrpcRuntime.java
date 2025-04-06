package io.axor.runtime.stream.grpc;

import com.google.common.net.HostAndPort;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import io.axor.runtime.DeadLetterHandlerFactory;
import io.axor.runtime.EventContext;
import io.axor.runtime.EventDispatcher;
import io.axor.runtime.MsgType;
import io.axor.runtime.Serde;
import io.axor.runtime.SerdeRegistry;
import io.axor.runtime.Signal;
import io.axor.runtime.Status;
import io.axor.runtime.StatusCode;
import io.axor.runtime.StreamAddress;
import io.axor.runtime.StreamChannel;
import io.axor.runtime.StreamDefinition;
import io.axor.runtime.StreamInChannel;
import io.axor.runtime.stream.grpc.StreamRecord.ContextMsg;
import io.axor.runtime.stream.grpc.StreamRecord.ContextSignal;
import io.axor.runtime.stream.grpc.proto.AxorProto;
import io.grpc.BindableService;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.Context;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import org.jetbrains.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

import static io.axor.runtime.stream.grpc.StreamUtils.endSignal;
import static io.axor.runtime.stream.grpc.StreamUtils.fromStatusException;
import static io.axor.runtime.stream.grpc.StreamUtils.toStatusException;

public class GrpcRuntime {
    private static final Logger LOG = LoggerFactory.getLogger(GrpcRuntime.class);
    private static final ThreadLocal<Metadata> METADATA_TL = new ThreadLocal<>();
    private static final String SERVICE_PREFIX = "_AXOR_";
    private static final String METHOD_NAME = "call";
    private static final io.grpc.Status HEADER_NOT_FOUND = io.grpc.Status.INVALID_ARGUMENT
            .withDescription("HEADER not found");

    private final CallOptions callOptions = CallOptions.DEFAULT;
    private final Metadata.Key<StreamDefinition<?>> clientStreamDefKey;
    private final Metadata.Key<StreamDefinition<?>> serverStreamDefKey;
    private final String serviceName;
    private final MethodDescriptor<InputStream, AxorProto.RemoteSignal> methodDescriptor;
    private final ServerServiceDefinition serviceDefinition;
    private final ServiceRegistry serviceRegistry;
    private final SerdeRegistry serdeRegistry;
    private final ChannelPool channelPool;
    private final DeadLetterHandlerFactory deadLetterHandler;

    public GrpcRuntime(String system,
                       SerdeRegistry registry,
                       ServiceRegistry serviceRegistry,
                       ChannelPool channelPool,
                       DeadLetterHandlerFactory deadLetterHandler) {
        this.serviceRegistry = serviceRegistry;
        this.serdeRegistry = registry;
        this.channelPool = channelPool;
        this.deadLetterHandler = deadLetterHandler;
        var marshaller = new StreamDefinitionBinaryMarshaller(registry);
        this.clientStreamDefKey = Metadata.Key.of("CDEF", marshaller);
        this.serverStreamDefKey = Metadata.Key.of("SDEF", marshaller);
        this.serviceName = SERVICE_PREFIX + system;
        this.methodDescriptor = MethodDescriptor.<InputStream, AxorProto.RemoteSignal>newBuilder()
                .setType(MethodDescriptor.MethodType.BIDI_STREAMING)
                .setFullMethodName(serviceName + "/" + METHOD_NAME)
                .setSampledToLocalTracing(true)
                .setRequestMarshaller(Marshallers.forwardingMarshaller())
                .setResponseMarshaller(ProtoUtils.marshaller(AxorProto.RemoteSignal.getDefaultInstance()))
                .build();
        this.serviceDefinition = new StreamService().bindService();
    }

    private static ContextCloseable createContext() {
        Context prev = Context.ROOT.attach();
        return () -> Context.ROOT.detach(prev);
    }

    public Metadata.Key<StreamDefinition<?>> getServerStreamNameKey() {
        return serverStreamDefKey;
    }

    @VisibleForTesting
    MethodDescriptor<InputStream, AxorProto.RemoteSignal> getMethodDescriptor() {
        return methodDescriptor;
    }

    public String getServiceName() {
        return serviceName;
    }

    public ServerServiceDefinition getServiceDefinition() {
        return serviceDefinition;
    }

    public <T> StreamChannel.StreamObserver<T> clientCall(Channel channel,
                                                          StreamDefinition<?> selfDef,
                                                          StreamDefinition<T> remoteDef,
                                                          EventDispatcher executor,
                                                          StreamChannel.Observer observer) {
        channel = ClientInterceptors.intercept(channel, new ClientInterceptor() {
            @Override
            public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT,
                                                                               RespT> method,
                                                                       CallOptions callOptions,
                                                                       Channel next) {
                try (var ignored = createContext()) {
                    ClientCall<ReqT, RespT> call = next.newCall(method, callOptions);
                    return new ForwardingClientCall.SimpleForwardingClientCall<>(call) {
                        @Override
                        public void start(Listener<RespT> responseListener, Metadata headers) {
                            headers.put(clientStreamDefKey, selfDef);
                            headers.put(serverStreamDefKey, remoteDef);
                            super.start(responseListener, headers);
                        }
                    };
                }
            }
        });
        CallOptions callOpt = callOptions.withExecutor(executor);
        ClientCall<InputStream, AxorProto.RemoteSignal> call = channel.newCall(methodDescriptor,
                callOpt);
        var res = new ResStatusObserver(remoteDef, selfDef, observer, executor, serdeRegistry);
        var req = ClientCalls.asyncBidiStreamingCall(call, res);
        var marshaller = new ContextMsgMarshaller<>(remoteDef.serde(), serdeRegistry);
        return new ReqObserverAdaptor<>(req, marshaller, executor);
    }

    private interface ContextCloseable extends Closeable {
        void close();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static class ResObserverAdaptor implements StreamObserver<InputStream> {
        private final EventDispatcher executor;
        private final StreamChannel.StreamObserver open;
        private final ContextMsgMarshaller<?> msgMarshaller;
        private final StreamObserver<AxorProto.RemoteSignal> resObserver;
        private boolean done;

        public ResObserverAdaptor(EventDispatcher executor,
                                  StreamChannel.StreamObserver open,
                                  ContextMsgMarshaller<?> msgMarshaller,
                                  StreamObserver<AxorProto.RemoteSignal> resObserver) {
            this.executor = executor;
            this.open = open;
            this.msgMarshaller = msgMarshaller;
            this.resObserver = resObserver;
            done = false;
        }

        @VisibleForTesting
        void setDone() {
            this.done = true;
        }

        @Override
        public void onNext(InputStream value) {
            assert executor.inExecutor();
            try {
                StreamRecord<?> ctxMsg = msgMarshaller.parse(value);
                if (ctxMsg instanceof ContextMsg<?>(var ctx, var msg)) {
                    executor.setContext(ctx);
                    open.onNext(msg);
                } else if (ctxMsg instanceof ContextSignal<?>(var ctx, var signal)) {
                    executor.setContext(ctx);
                    open.onSignal(signal);
                }
            } catch (Throwable e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Message send error", e);
                }
                onError(e);
            } finally {
                executor.setContext(EventContext.INITIAL);
            }
        }

        @Override
        public void onError(Throwable t) {
            if (done) {
                return;
            }
            try {
                assert executor.inExecutor();
                done = true;
                open.onEnd(fromStatusException(t));
            } finally {
                try (var ignored = createContext()) {
                    resObserver.onCompleted();
                }
            }
        }

        @Override
        public void onCompleted() {
            try {
                assert executor.inExecutor();
                if (done) {
                    return;
                }
                done = true;
                open.onEnd(StatusCode.COMPLETE.toStatus());
            } finally {
                try (var ignored = createContext()) {
                    resObserver.onCompleted();
                }
            }
        }
    }

    static class ReqObserverAdaptor<T> implements StreamChannel.StreamObserver<T> {
        private final StreamObserver<InputStream> req;
        private final ContextMsgMarshaller<T> marshaller;
        private final EventDispatcher dispatcher;
        private boolean done;

        public ReqObserverAdaptor(StreamObserver<InputStream> req,
                                  ContextMsgMarshaller<T> marshaller,
                                  EventDispatcher dispatcher) {
            this.req = req;
            this.marshaller = marshaller;
            this.dispatcher = dispatcher;
            done = false;
        }

        @Override
        public void onEnd(Status status) {
            if (done) {
                throw new IllegalArgumentException("already completed");
            }
            done = true;
            if (status.code() == StatusCode.COMPLETE.code) {
                req.onCompleted();
            } else {
                req.onError(toStatusException(status));
            }
        }

        @Override
        public void onSignal(Signal signal) {
            if (done) {
                throw new IllegalArgumentException("already completed");
            }
            req.onNext(marshaller.stream(new ContextSignal<>(dispatcher.getContext(), signal)));
        }

        @Override
        public void onNext(T t) {
            if (done) {
                throw new IllegalArgumentException("already completed");
            }
            req.onNext(marshaller.stream(new ContextMsg<>(dispatcher.getContext(), t)));
        }
    }

    static class ResStatusObserver implements StreamObserver<AxorProto.RemoteSignal> {
        private final StreamDefinition<?> remoteDefinition;
        private final StreamDefinition<?> selfDefinition;
        private final StreamChannel.Observer observer;
        private final EventDispatcher executor;
        private final MethodDescriptor.Marshaller<Signal> signalMarshaller;
        private boolean done;

        public ResStatusObserver(StreamDefinition<?> remoteDefinition,
                                 StreamDefinition<?> selfDefinition,
                                 StreamChannel.Observer observer,
                                 EventDispatcher executor,
                                 SerdeRegistry serdeRegistry) {
            this.remoteDefinition = remoteDefinition;
            this.selfDefinition = selfDefinition;
            this.observer = observer;
            this.executor = executor;
            Serde<Signal> signalSerde = serdeRegistry.create(MsgType.of(Signal.class));
            this.signalMarshaller = new MarshallerAdaptor<>(signalSerde);
            done = false;
        }

        @Override
        public void onNext(AxorProto.RemoteSignal signal) {
            if (signal.getEndOfStream()) {
                AxorProto.ResStatus v;
                try {
                    v = AxorProto.ResStatus.parseFrom(signal.getContent());
                } catch (InvalidProtocolBufferException e) {
                    throw new RuntimeException(e);
                }
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Receive res status[code={}, msg={}] from {} to {}", v.getCode(),
                            v.getMessage(), remoteDefinition.address(), selfDefinition.address());

                }
                if (done) {
                    return;
                }
                done = true;
                try (var ignored = createContext()) {
                    observer.onEnd(StreamUtils.fromProto(v));
                }
            } else {
                observer.onSignal(signalMarshaller.parse(signal.getContent().newInput()));
            }
        }

        @Override
        public void onError(Throwable t) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Error on client call from {} to {}",
                        remoteDefinition.address(), selfDefinition.address(), t);
            }
            assert executor.inExecutor();
            if (done) {
                return;
            }
            done = true;
            try (var ignored = createContext()) {
                observer.onEnd(fromStatusException(t));
            }
        }

        @Override
        public void onCompleted() {
            assert executor.inExecutor();
            if (done) {
                return;
            }
            done = true;
            try (var ignored = createContext()) {
                observer.onEnd(StatusCode.COMPLETE.toStatus());
            }
        }
    }

    private static class SafeStreamObserver<T> implements StreamObserver<T> {
        private final StreamObserver<T> delegate;
        private boolean done = false;

        private SafeStreamObserver(StreamObserver<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onNext(T value) {
            if (done) {
                return;
            }
            delegate.onNext(value);
        }

        @Override
        public void onError(Throwable t) {
            if (done) {
                return;
            }
            delegate.onError(t);
            done = true;
        }

        @Override
        public void onCompleted() {
            if (done) {
                return;
            }
            delegate.onCompleted();
            done = true;
        }
    }

    class StreamService implements BindableService {

        private StreamObserver<InputStream> call(StreamObserver<AxorProto.RemoteSignal> ob) {
            var resObserver = new SafeStreamObserver<>(ob);
            Metadata headers = METADATA_TL.get();
            StreamDefinition<?> clientStreamDef = headers.get(clientStreamDefKey);
            StreamDefinition<?> serverStreamDef = headers.get(serverStreamDefKey);
            if (clientStreamDef == null || serverStreamDef == null) {
                throw HEADER_NOT_FOUND.asRuntimeException();
            }
            StreamAddress address = clientStreamDef.address();
            channelPool.notifyAlive(HostAndPort.fromParts(address.host(), address.port()));
            StreamInChannel<?> channel = serviceRegistry.getChannel(serverStreamDef);
            boolean streamUnavailable = false;
            if (channel == null) {
                resObserver.onNext(StreamUtils.endSignal(StatusCode.UNAVAILABLE.code,
                        serverStreamDef.address() + " not available"));
                streamUnavailable = true;
            } else {
                Serde<?> expected = channel.getSelfDefinition().serde();
                Serde<?> actual = serverStreamDef.serde();
                if (!expected.getType().equals(actual.getType())) {
                    resObserver.onNext(StreamUtils.endSignal(StatusCode.MSG_TYPE_MISMATCH.code,
                            "expect type: " + expected.getType() + " but found: " + actual.getType()));
                    streamUnavailable = true;
                } else if (!expected.getImpl().equals(actual.getImpl())) {
                    resObserver.onNext(StreamUtils.endSignal(StatusCode.SERDE_MISMATCH.code,
                            "expect serde: " + expected.getImpl() + " but found: " + actual.getImpl()
                    ));
                    streamUnavailable = true;
                }
            }
            if (streamUnavailable) {
                var msgMarshaller = new ContextMsgMarshaller<>(serverStreamDef.serde(),
                        serdeRegistry);
                var handler = deadLetterHandler.create(clientStreamDef, serverStreamDef);
                return new StreamObserver<>() {
                    @Override
                    public void onNext(InputStream value) {
                        StreamRecord<?> parsed = msgMarshaller.parse(value);
                        if (parsed instanceof StreamRecord.ContextMsg<?>(var ignore, var msg)) {
                            handler.handle(msg);
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Unexpected error while receiving dead letter", t);
                        }
                        try (var ignored = createContext()) {
                            resObserver.onCompleted();
                        }
                    }

                    @Override
                    public void onCompleted() {
                        try (var ignored = createContext()) {
                            resObserver.onCompleted();
                        }
                    }
                };
            }
            LOG.debug("New stream open from {} to {}", address, serverStreamDef.address());
            EventDispatcher executor = EventDispatcher.current();
            assert executor != null;
            Serde<Signal> signalSerde = serdeRegistry.create(MsgType.of(Signal.class));
            var signalMarshaller = new MarshallerAdaptor<>(signalSerde);
            @SuppressWarnings("rawtypes")
            StreamChannel.StreamObserver open = channel.open(clientStreamDef, executor,
                    new StreamChannel.Observer() {

                        @Override
                        public void onSignal(Signal signal) {
                            try (InputStream stream = signalMarshaller.stream(signal)) {
                                resObserver.onNext(AxorProto.RemoteSignal.newBuilder()
                                        .setEndOfStream(false)
                                        .setContent(ByteString.readFrom(stream))
                                        .build());
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }

                        @Override
                        public void onEnd(Status status) {
                            resObserver.onNext(endSignal(status));
                        }
                    });
            var msgMarshaller = new ContextMsgMarshaller<>(channel.getSelfDefinition().serde(),
                    serdeRegistry);
            return new ResObserverAdaptor(executor, open, msgMarshaller, resObserver);
        }

        @Override
        public ServerServiceDefinition bindService() {
            ServerServiceDefinition serverServiceDefinition = ServerServiceDefinition
                    .builder(serviceName)
                    .addMethod(methodDescriptor, ServerCalls.asyncBidiStreamingCall(this::call))
                    .build();
            return ServerInterceptors.intercept(serverServiceDefinition, new ServerInterceptor() {
                @Override
                public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT,
                                                                                     RespT> call,
                                                                             Metadata headers,
                                                                             ServerCallHandler<ReqT, RespT> next) {
                    METADATA_TL.set(headers);
                    try (var ignored = createContext()) {
                        return next.startCall(call, headers);
                    } finally {
                        METADATA_TL.remove();
                    }
                }
            });
        }
    }
}
