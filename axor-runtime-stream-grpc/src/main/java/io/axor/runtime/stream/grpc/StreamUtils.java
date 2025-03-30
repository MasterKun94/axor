package io.axor.runtime.stream.grpc;

import io.axor.api.ActorAddress;
import io.axor.commons.RuntimeUtil;
import io.axor.runtime.MsgType;
import io.axor.runtime.SerdeRegistry;
import io.axor.runtime.Status;
import io.axor.runtime.StatusCode;
import io.axor.runtime.Unsafe;
import io.axor.runtime.stream.grpc.proto.AxorProto;
import io.grpc.Metadata;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class StreamUtils {
    @VisibleForTesting
    static final Metadata.Key<Integer> STATUS_KEY = Metadata.Key.of("CODE-bin",
            new IntBinaryMarshaller());
    @VisibleForTesting
    static final AxorProto.ResStatus COMPLETE_STATUS = AxorProto.ResStatus.newBuilder()
            .setCode(StatusCode.COMPLETE.code)
            .build();

    private static io.grpc.Status toGrpcStatus(Status status) {
        return io.grpc.Status.INTERNAL
                .withCause(status.cause())
                .withDescription(status.codeStatus().toString());
    }

    public static io.grpc.Status toGrpcStatus(Throwable e) {
        return io.grpc.Status.INTERNAL
                .withDescription(e.getMessage())
                .withCause(e);
    }

    public static StatusException toStatusException(Status status) {
        Metadata metadata = new Metadata();
        metadata.put(STATUS_KEY, status.code());
        return new StatusException(toGrpcStatus(status), metadata);
    }

    public static StatusRuntimeException toStatusRuntimeException(Status status) {
        Metadata metadata = new Metadata();
        metadata.put(STATUS_KEY, status.code());
        return new StatusRuntimeException(toGrpcStatus(status), metadata);
    }

    public static Status fromStatusException(Throwable e) {
        Metadata metadata;
        io.grpc.Status status;
        if (e instanceof StatusException se) {
            status = se.getStatus();
            metadata = se.getTrailers();
        } else if (e instanceof StatusRuntimeException sre) {
            status = sre.getStatus();
            metadata = sre.getTrailers();
        } else {
            return StatusCode.UNKNOWN.toStatus(e);
        }
        if (metadata == null) {
            return fromGrpcStatus(status, e);
        }
        Integer code = metadata.get(STATUS_KEY);
        if (code == null) {
            return fromGrpcStatus(status, e);
        }
        return StatusCode.fromCode(code).toStatus(e);
    }

    public static Status fromGrpcStatus(io.grpc.Status status, Throwable e) {
        return switch (status.getCode()) {
            case OK -> StatusCode.COMPLETE.toStatus();
            case CANCELLED -> StatusCode.CANCELLED.toStatus(e);
            case UNAVAILABLE -> StatusCode.UNAVAILABLE.toStatus(e);
            case INTERNAL -> StatusCode.SYSTEM_ERROR.toStatus(e);
            case UNAUTHENTICATED -> StatusCode.UNAUTHENTICATED.toStatus(e);
            case null, default -> StatusCode.UNKNOWN.toStatus(e);
        };
    }

    public static Status fromProto(AxorProto.ResStatus resStatus) {
        return !resStatus.getMessage().isEmpty() ?
                resStatus.getCode() == StatusCode.COMPLETE.code ?
                        StatusCode.COMPLETE.toStatus() :
                        new Status(resStatus.getCode(), new IOException(resStatus.getMessage())) :
                new Status(resStatus.getCode(), null);
    }

    public static AxorProto.ResStatus toProto(Status status) {
        if (status.code() == StatusCode.COMPLETE.code) {
            return COMPLETE_STATUS;
        }
        AxorProto.ResStatus.Builder builder = AxorProto.ResStatus.newBuilder()
                .setCode(status.code());
        if (status.cause() != null) {
            builder.setMessage(RuntimeUtil.toSimpleString(status.cause()));
        }
        return builder.build();
    }

    public static AxorProto.ActorAddress actorAddressToProto(ActorAddress address) {
        return AxorProto.ActorAddress.newBuilder()
                .setSystem(address.system())
                .setHost(address.host())
                .setPort(address.port())
                .setName(address.name())
                .build();
    }

    public static ActorAddress protoToActorAddress(AxorProto.ActorAddress proto) {
        return ActorAddress.create(proto.getSystem(), proto.getHost(), proto.getPort(),
                proto.getName());
    }

    public static AxorProto.MsgType msgTypeToProto(MsgType<?> msgType, SerdeRegistry registry) {
        int id = registry.findIdByType(msgType);
        AxorProto.MsgType.Builder builder = AxorProto.MsgType.newBuilder();
        if (id != -1) {
            builder.setIdType(id);
        } else {
            builder.setStringType(msgType.type().getName());
            for (MsgType<?> typeArg : msgType.typeArgs()) {
                builder.addTypeArgs(msgTypeToProto(typeArg, registry));
            }
        }
        return builder.build();
    }

    public static MsgType<?> protoToMsgType(AxorProto.MsgType msgType, SerdeRegistry registry) {
        if (msgType.hasIdType()) {
            return registry.getTypeById(msgType.getIdType());
        }
        if (msgType.hasStringType()) {
            Class<?> type;
            try {
                type = Class.forName(msgType.getStringType());
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
            int cnt = msgType.getTypeArgsCount();
            if (cnt == 0) {
                return MsgType.of(type);
            }
            List<MsgType<?>> typeArgs = new ArrayList<>(cnt);
            for (int i = 0; i < cnt; i++) {
                typeArgs.add(protoToMsgType(msgType.getTypeArgs(i), registry));
            }
            return Unsafe.msgType(type, typeArgs);
        }
        throw new IllegalArgumentException("should never happen");
    }
}
