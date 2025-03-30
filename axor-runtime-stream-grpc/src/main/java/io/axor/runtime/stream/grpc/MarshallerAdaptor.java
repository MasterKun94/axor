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

public class MarshallerAdaptor<T> implements MethodDescriptor.Marshaller<T> {
    private final Serde<T> serde;

    public MarshallerAdaptor(Serde<T> serde) {
        this.serde = serde;
    }

    @Override
    public InputStream stream(T value) {
        InputStream stream;
        try {
            stream = serde.serialize(value);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return stream instanceof Serde.Drainable ?
                stream instanceof Serde.KnownLength ?
                        new InputStreamKnownLengthDrainable(stream) :
                        new InputStreamDrainable(stream) :
                stream instanceof Serde.KnownLength ?
                        new InputStreamKnownLength(stream) :
                        stream;
    }

    @Override
    public T parse(InputStream stream) {
        try {
            return serde.deserialize(
                    stream instanceof Drainable ?
                            stream instanceof KnownLength ?
                                    new InputStreamKnownLengthDrainable(stream) :
                                    new InputStreamDrainable(stream) :
                            stream instanceof KnownLength ?
                                    new InputStreamKnownLength(stream) :
                                    stream);
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
