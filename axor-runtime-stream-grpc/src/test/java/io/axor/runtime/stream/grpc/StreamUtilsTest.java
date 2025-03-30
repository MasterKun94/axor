package io.axor.runtime.stream.grpc;

import io.axor.runtime.Status;
import io.axor.runtime.StatusCode;
import io.grpc.Metadata;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StreamUtilsTest {

    @Test
    public void testFromStatusExceptionWithStatusException() {
        Metadata metadata = new Metadata();
        metadata.put(StreamUtils.STATUS_KEY, 100);
        StatusException statusException =
                new StatusException(io.grpc.Status.INTERNAL.withDescription("Internal error"),
                        metadata);
        Status result = StreamUtils.fromStatusException(statusException);
        assertEquals(100, result.code());
    }

    @Test
    public void testFromStatusExceptionWithStatusRuntimeException() {
        Metadata metadata = new Metadata();
        metadata.put(StreamUtils.STATUS_KEY, 100);
        StatusRuntimeException statusRuntimeException =
                new StatusRuntimeException(io.grpc.Status.INTERNAL.withDescription("Internal " +
                                                                                   "error"),
                        metadata);
        Status result = StreamUtils.fromStatusException(statusRuntimeException);
        assertEquals(100, result.code());
    }

    @Test
    public void testFromStatusExceptionWithOtherThrowable() {
        Throwable throwable = new RuntimeException("Generic error");
        Status result = StreamUtils.fromStatusException(throwable);
        assertEquals(StatusCode.UNKNOWN.code, result.code());
    }

    @Test
    public void testFromStatusExceptionWithNullMetadata() {
        StatusException statusException =
                new StatusException(io.grpc.Status.INTERNAL.withDescription("Internal error"));
        Status result = StreamUtils.fromStatusException(statusException);
        assertEquals(StatusCode.SYSTEM_ERROR.code, result.code());
    }

    @Test
    public void testFromStatusExceptionWithMissingCodeInMetadata() {
        Metadata metadata = new Metadata();
        StatusException statusException =
                new StatusException(io.grpc.Status.INTERNAL.withDescription("Internal error"),
                        metadata);
        Status result = StreamUtils.fromStatusException(statusException);
        assertEquals(StatusCode.SYSTEM_ERROR.code, result.code());
    }
}
