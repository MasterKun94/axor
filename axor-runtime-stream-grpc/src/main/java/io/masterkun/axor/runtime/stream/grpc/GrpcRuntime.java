package io.masterkun.axor.runtime.stream.grpc;

import com.google.common.net.HostAndPort;
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
import io.masterkun.axor.runtime.DeadLetterHandlerFactory;
import io.masterkun.axor.runtime.EventDispatcher;
import io.masterkun.axor.runtime.Serde;
import io.masterkun.axor.runtime.SerdeRegistry;
import io.masterkun.axor.runtime.Status;
import io.masterkun.axor.runtime.StatusCode;
import io.masterkun.axor.runtime.StreamAddress;
import io.masterkun.axor.runtime.StreamChannel;
import io.masterkun.axor.runtime.StreamDefinition;
import io.masterkun.axor.runtime.StreamInChannel;
import io.masterkun.axor.runtime.stream.grpc.proto.AxorProto.ResStatus;
import org.jetbrains.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.InputStream;

import static io.masterkun.axor.runtime.stream.grpc.StreamUtils.fromStatusException;
import static io.masterkun.axor.runtime.stream.grpc.StreamUtils.toStatusException;

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
    private final MethodDescriptor<InputStream, ResStatus> methodDescriptor;
    private final ServerServiceDefinition serviceDefinition;
    private final ServiceRegistry serviceRegistry;
    private final ChannelPool channelPool;
    private final DeadLetterHandlerFactory deadLetterHandler;

    public GrpcRuntime(String system,
                       SerdeRegistry registry,
                       ServiceRegistry serviceRegistry,
                       ChannelPool channelPool,
                       DeadLetterHandlerFactory deadLetterHandler) {
        this.serviceRegistry = serviceRegistry;
        this.channelPool = channelPool;
        this.deadLetterHandler = deadLetterHandler;
        var marshaller = new StreamDefinitionBinaryMarshaller(registry);
        this.clientStreamDefKey = Metadata.Key.of("CDEF", marshaller);
        this.serverStreamDefKey = Metadata.Key.of("SDEF", marshaller);
        this.serviceName = SERVICE_PREFIX + system;
        this.methodDescriptor = MethodDescriptor.<InputStream, ResStatus>newBuilder()
                .setType(MethodDescriptor.MethodType.BIDI_STREAMING)
                .setFullMethodName(serviceName + "/" + METHOD_NAME)
                .setSampledToLocalTracing(true)
                .setRequestMarshaller(Marshallers.forwardingMarshaller())
                .setResponseMarshaller(ProtoUtils.marshaller(ResStatus.getDefaultInstance()))
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
    MethodDescriptor<InputStream, ResStatus> getMethodDescriptor() {
        return methodDescriptor;
    }

    public String getServiceName() {
        return serviceName;
    }

    public ServerServiceDefinition getServiceDefinition() {
        return serviceDefinition;
    }

    public <T> StreamChannel.StreamObserver<T> clientCall(Channel channel,
                                                          StreamDefinition<?> selfDefinition,
                                                          StreamDefinition<T> definition,
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
                            headers.put(clientStreamDefKey, selfDefinition);
                            headers.put(serverStreamDefKey, definition);
                            super.start(responseListener, headers);
                        }
                    };
                }
            }
        });
        CallOptions callOpt = callOptions.withExecutor(executor);
        ClientCall<InputStream, ResStatus> call = channel.newCall(methodDescriptor, callOpt);
        var res = new ResStatusObserver(definition, selfDefinition, observer, executor);
        var req = ClientCalls.asyncBidiStreamingCall(call, res);
        var marshaller = Marshallers.create(definition.serde());
        return new ReqObserverAdaptor<>(req, marshaller);
    }

    private interface ContextCloseable extends Closeable {
        void close();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static class ResObserverAdaptor implements StreamObserver<InputStream> {
        private final EventDispatcher executor;
        private final StreamChannel.StreamObserver open;
        private final MethodDescriptor.Marshaller<?> msgMarshaller;
        private final StreamObserver<ResStatus> resObserver;
        private boolean done;

        public ResObserverAdaptor(EventDispatcher executor,
                                  StreamChannel.StreamObserver open,
                                  MethodDescriptor.Marshaller<?> msgMarshaller,
                                  StreamObserver<ResStatus> resObserver) {
            this.executor = executor;
            this.open = open;
            this.msgMarshaller = msgMarshaller;
            this.resObserver = resObserver;
            done = false;
        }

        @VisibleForTesting
        void setDone(boolean done) {
            this.done = done;
        }

        @Override
        public void onNext(InputStream value) {
            assert executor.inExecutor();
            try {
                open.onNext(msgMarshaller.parse(value));
            } catch (Throwable e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Message send error", e);
                }
                onError(e);
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
        private final MethodDescriptor.Marshaller<T> marshaller;
        private boolean done;

        public ReqObserverAdaptor(StreamObserver<InputStream> req,
                                  MethodDescriptor.Marshaller<T> marshaller) {
            this.req = req;
            this.marshaller = marshaller;
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
        public void onNext(T t) {
            if (done) {
                throw new IllegalArgumentException("already completed");
            }
            req.onNext(marshaller.stream(t));
        }
    }

    static class ResStatusObserver implements StreamObserver<ResStatus> {
        private final StreamDefinition<?> remoteDefinition;
        private final StreamDefinition<?> selfDefinition;
        private final StreamChannel.Observer observer;
        private final EventDispatcher executor;
        private boolean done;

        public ResStatusObserver(StreamDefinition<?> remoteDefinition,
                                 StreamDefinition<?> selfDefinition,
                                 StreamChannel.Observer observer,
                                 EventDispatcher executor) {
            this.remoteDefinition = remoteDefinition;
            this.selfDefinition = selfDefinition;
            this.observer = observer;
            this.executor = executor;
            done = false;
        }

        @Override
        public void onNext(ResStatus value) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Receive res status[code={}, msg={}] from {} to {}", value.getCode(),
                        value.getMessage(), remoteDefinition.address(), selfDefinition.address());
            }
            if (done) {
                return;
            }
            done = true;
            try (var ignored = createContext()) {
                observer.onEnd(StreamUtils.fromProto(value));
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

        private StreamObserver<InputStream> call(StreamObserver<ResStatus> ob) {
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
                resObserver.onNext(ResStatus.newBuilder()
                        .setCode(StatusCode.UNAVAILABLE.code)
                        .setMessage(serverStreamDef.address() + " not available")
                        .build());
                streamUnavailable = true;
            } else {
                Serde<?> expected = channel.getSelfDefinition().serde();
                Serde<?> actual = serverStreamDef.serde();
                if (!expected.getType().equals(actual.getType())) {
                    resObserver.onNext(ResStatus.newBuilder()
                            .setCode(StatusCode.MSG_TYPE_MISMATCH.code)
                            .setMessage("expect type: " + expected.getType() + " but found: " + actual.getType())
                            .build());
                    streamUnavailable = true;
                } else if (!expected.getImpl().equals(actual.getImpl())) {
                    resObserver.onNext(ResStatus.newBuilder()
                            .setCode(StatusCode.SERDE_MISMATCH.code)
                            .setMessage("expect serde: " + expected.getImpl() + " but found: " + actual.getImpl())
                            .build());
                    streamUnavailable = true;
                }
            }
            if (streamUnavailable) {
                var msgMarshaller = Marshallers.create(serverStreamDef.serde());
                var handler = deadLetterHandler.create(clientStreamDef, serverStreamDef);
                return new StreamObserver<>() {
                    @Override
                    public void onNext(InputStream value) {
                        handler.handle(msgMarshaller.parse(value));
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
            @SuppressWarnings("rawtypes")
            StreamChannel.StreamObserver open = channel.open(clientStreamDef, executor,
                    status -> resObserver.onNext(StreamUtils.toProto(status)));
            var msgMarshaller = Marshallers.create(channel.getSelfDefinition().serde());
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
