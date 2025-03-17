package io.masterkun.kactor.runtime.stream.grpc;

import io.grpc.Metadata;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.masterkun.kactor.api.ActorAddress;
import io.masterkun.kactor.commons.RuntimeUtil;
import io.masterkun.kactor.runtime.MsgType;
import io.masterkun.kactor.runtime.SerdeRegistry;
import io.masterkun.kactor.runtime.Status;
import io.masterkun.kactor.runtime.StatusCode;
import io.masterkun.kactor.runtime.Unsafe;
import io.masterkun.kactor.runtime.stream.grpc.proto.KActorProto;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class StreamUtils {
    private static final Metadata.Key<Integer> STATUS_KEY = Metadata.Key.of("CODE-bin", new IntBinaryMarshaller());
    private static final KActorProto.ResStatus COMPLETE_STATUS = KActorProto.ResStatus.newBuilder()
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

    public static Status fromProto(KActorProto.ResStatus resStatus) {
        return !resStatus.getMessage().isEmpty() ?
                resStatus.getCode() == StatusCode.COMPLETE.code ?
                        StatusCode.COMPLETE.toStatus() :
                        new Status(resStatus.getCode(), new IOException(resStatus.getMessage())) :
                new Status(resStatus.getCode(), null);
    }

    public static KActorProto.ResStatus toProto(Status status) {
        if (status.code() == StatusCode.COMPLETE.code) {
            return COMPLETE_STATUS;
        }
        KActorProto.ResStatus.Builder builder = KActorProto.ResStatus.newBuilder()
                .setCode(status.code());
        if (status.cause() != null) {
            builder.setMessage(RuntimeUtil.toSimpleString(status.cause()));
        }
        return builder.build();
    }

    public static KActorProto.ActorAddress actorAddressToProto(ActorAddress address) {
        return KActorProto.ActorAddress.newBuilder()
                .setSystem(address.system())
                .setHost(address.host())
                .setPort(address.port())
                .setName(address.name())
                .build();
    }

    public static ActorAddress protoToActorAddress(KActorProto.ActorAddress proto) {
        return ActorAddress.create(proto.getSystem(), proto.getHost(), proto.getPort(), proto.getName());
    }

    public static KActorProto.MsgType msgTypeToProto(MsgType<?> msgType, SerdeRegistry registry) {
        int id = registry.findIdByType(msgType);
        KActorProto.MsgType.Builder builder = KActorProto.MsgType.newBuilder();
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

    public static MsgType<?> protoToMsgType(KActorProto.MsgType msgType, SerdeRegistry registry) {
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
