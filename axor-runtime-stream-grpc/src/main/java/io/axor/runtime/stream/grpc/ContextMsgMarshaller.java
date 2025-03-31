package io.axor.runtime.stream.grpc;

import io.axor.exception.ActorIOException;
import io.axor.exception.ActorNotFoundException;
import io.axor.exception.ActorRuntimeException;
import io.axor.exception.IllegalMsgTypeException;
import io.axor.runtime.Serde;
import io.axor.runtime.StatusCode;
import io.grpc.Drainable;
import io.grpc.KnownLength;
import io.grpc.MethodDescriptor;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ContextMsgMarshaller<T> implements MethodDescriptor.Marshaller<ContextMsg<T>> {
    private final ContextMsgSerde<T> serde;

    public ContextMsgMarshaller(Serde<T> serde) {
        this.serde = new ContextMsgSerde<>(serde);
    }

    @Override
    public InputStream stream(ContextMsg<T> value) {
        return new InputStreamKnownLengthDrainable(serde.serialize(value));
    }

    @Override
    public ContextMsg<T> parse(InputStream stream) {
        try {
            if (stream instanceof Drainable) {
                if (stream instanceof KnownLength) {
                    stream = new InputStreamKnownLengthDrainable(stream);
                } else {
                    stream = new InputStreamDrainable(stream);
                }
            } else if (stream instanceof KnownLength) {
                stream = new InputStreamKnownLength(stream);
            }
            return serde.deserialize(stream);
        } catch (ActorIOException | ActorRuntimeException e) {
            StatusCode code = switch (e.getActorExceptionCause()) {
                case ActorNotFoundException ignored -> StatusCode.ACTOR_NOT_FOUND;
                case IllegalMsgTypeException ignored -> StatusCode.MSG_TYPE_MISMATCH;
            };
            throw StreamUtils.toStatusRuntimeException(code.toStatus(e));
        } catch (IOException e) {
            throw StreamUtils.toGrpcStatus(e).asRuntimeException();
        }
    }

    private static class InputStreamKnownLength extends FilterInputStream implements KnownLength,
            Serde.KnownLength {
        public InputStreamKnownLength(InputStream in) {
            super(in);
        }

        @Override
        public int available() throws IOException {
            return ((Serde.KnownLength) in).available();
        }
    }

    private static class InputStreamDrainable extends FilterInputStream implements Drainable,
            Serde.Drainable {

        public InputStreamDrainable(InputStream in) {
            super(in);
        }

        @Override
        public int drainTo(OutputStream target) throws IOException {
            return ((Serde.Drainable) in).drainTo(target);
        }
    }

    private static final class InputStreamKnownLengthDrainable extends InputStreamKnownLength implements Drainable, Serde.Drainable {

        InputStreamKnownLengthDrainable(InputStream in) {
            super(in);
        }

        @Override
        public int drainTo(OutputStream target) throws IOException {
            return ((Serde.Drainable) in).drainTo(target);
        }
    }
}
