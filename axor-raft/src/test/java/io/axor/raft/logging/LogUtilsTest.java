package io.axor.raft.logging;

import com.google.protobuf.ByteString;
import io.axor.commons.util.ByteArray;
import io.axor.raft.RaftException;
import io.axor.raft.proto.PeerProto;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;

/**
 * Tests for the LogUtils class.
 */
public class LogUtilsTest {

    @Test
    public void testToBytesLogIdWithByteBuffer() throws RaftException {
        // Create a test LogId
        PeerProto.LogId logId = PeerProto.LogId.newBuilder()
                .setIndex(123L)
                .setTerm(456L)
                .build();

        // Create a ByteBuffer to reuse
        ByteBuffer buffer = ByteBuffer.allocate(16);

        // Convert LogId to bytes
        ByteBuffer result = LogUtils.toBytes(logId, buffer);

        // Verify the result
        Assert.assertEquals(0, result.position());
        Assert.assertEquals(16, result.limit());
        Assert.assertEquals(123L, result.getLong());
        Assert.assertEquals(456L, result.getLong());
    }

    @Test
    public void testToBytesLogId() {
        // Create a test LogId
        PeerProto.LogId logId = PeerProto.LogId.newBuilder()
                .setIndex(123L)
                .setTerm(456L)
                .build();

        // Convert LogId to bytes
        byte[] result = LogUtils.toBytes(logId);

        // Verify the result
        Assert.assertEquals(16, result.length);
        Assert.assertEquals(123L, ByteArray.getLong(result, 0));
        Assert.assertEquals(456L, ByteArray.getLong(result, 8));
    }

    @Test
    public void testToBytesLogValueWithByteBuffer() throws RaftException {
        // Create a test LogValue
        PeerProto.LogValue logValue = PeerProto.LogValue.newBuilder()
                .setData(ByteString.copyFromUtf8("test data"))
                .build();

        // Create a ByteBuffer to reuse
        ByteBuffer buffer = ByteBuffer.allocate(100);

        // Convert LogValue to bytes
        ByteBuffer result = LogUtils.toBytes(logValue, buffer);

        // Verify the result
        Assert.assertEquals(0, result.position());
        Assert.assertTrue(result.limit() > 0);

        // Convert back to LogValue and verify
        PeerProto.LogValue parsedValue = LogUtils.toValue(result);
        Assert.assertEquals(logValue.getData(), parsedValue.getData());
    }

    @Test
    public void testToBytesLogValue() {
        // Create a test LogValue
        PeerProto.LogValue logValue = PeerProto.LogValue.newBuilder()
                .setData(ByteString.copyFromUtf8("test data"))
                .build();

        // Convert LogValue to bytes
        byte[] result = LogUtils.toBytes(logValue);

        // Verify the result
        Assert.assertTrue(result.length > 0);

        // Convert back to LogValue and verify
        PeerProto.LogValue parsedValue = LogUtils.toValue(result);
        Assert.assertEquals(logValue.getData(), parsedValue.getData());
    }

    @Test
    public void testToIdFromByteBuffer() {
        // Create a ByteBuffer with test data
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(123L);
        buffer.putLong(456L);
        buffer.flip();

        // Convert ByteBuffer to LogId
        PeerProto.LogId logId = LogUtils.toId(buffer);

        // Verify the result
        Assert.assertEquals(123L, logId.getIndex());
        Assert.assertEquals(456L, logId.getTerm());
    }

    @Test
    public void testToIdFromByteArray() {
        // Create a byte array with test data
        byte[] bytes = new byte[16];
        ByteArray.setLong(bytes, 0, 123L);
        ByteArray.setLong(bytes, 8, 456L);

        // Convert byte array to LogId
        PeerProto.LogId logId = LogUtils.toId(bytes);

        // Verify the result
        Assert.assertEquals(123L, logId.getIndex());
        Assert.assertEquals(456L, logId.getTerm());
    }

    @Test
    public void testToValueFromByteBuffer() {
        // Create a test LogValue
        PeerProto.LogValue originalValue = PeerProto.LogValue.newBuilder()
                .setData(ByteString.copyFromUtf8("test data"))
                .build();

        // Convert to ByteBuffer
        ByteBuffer buffer = ByteBuffer.wrap(originalValue.toByteArray());

        // Convert ByteBuffer to LogValue
        PeerProto.LogValue logValue = LogUtils.toValue(buffer);

        // Verify the result
        Assert.assertEquals(originalValue.getData(), logValue.getData());
    }

    @Test
    public void testToValueFromByteArray() {
        // Create a test LogValue
        PeerProto.LogValue originalValue = PeerProto.LogValue.newBuilder()
                .setData(ByteString.copyFromUtf8("test data"))
                .build();

        // Convert to byte array
        byte[] bytes = originalValue.toByteArray();

        // Convert byte array to LogValue
        PeerProto.LogValue logValue = LogUtils.toValue(bytes);

        // Verify the result
        Assert.assertEquals(originalValue.getData(), logValue.getData());
    }

    @Test(expected = RuntimeException.class)
    public void testToValueFromInvalidByteBuffer() {
        // Create an invalid ByteBuffer
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putInt(123);
        buffer.flip();

        // This should throw a RuntimeException
        LogUtils.toValue(buffer);
    }

    @Test(expected = RuntimeException.class)
    public void testToValueFromInvalidByteArray() {
        // Create an invalid byte array
        byte[] bytes = new byte[4];
        ByteArray.setInt(bytes, 0, 123);

        // This should throw a RuntimeException
        LogUtils.toValue(bytes);
    }
}
