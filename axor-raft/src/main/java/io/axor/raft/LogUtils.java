package io.axor.raft;

import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;
import io.axor.raft.proto.PeerProto;

import java.io.IOException;
import java.nio.ByteBuffer;

public class LogUtils {
    public static ByteBuffer toBytes(MessageLite msg, ByteBuffer reuse) throws RaftException {
        reuse.clear();
        CodedOutputStream out = CodedOutputStream.newInstance(reuse);
        try {
            msg.writeTo(out);
            out.flush();
        } catch (IOException e) {
            throw new RaftException(e);
        }
        return reuse.flip();
    }

    public static byte[] toBytes(MessageLite msg) {
        return msg.toByteArray();
    }

    public static PeerProto.LogId toId(ByteBuffer value) {
        return fromBytes(value, PeerProto.LogId.parser());
    }

    public static PeerProto.LogId toId(byte[] value) {
        return fromBytes(value, PeerProto.LogId.parser());
    }

    public static PeerProto.LogValue toValue(ByteBuffer value) {
        return fromBytes(value, PeerProto.LogValue.parser());
    }

    public static PeerProto.LogValue toValue(byte[] value) {
        return fromBytes(value, PeerProto.LogValue.parser());
    }

    private static <T extends MessageLite> T fromBytes(ByteBuffer value, Parser<T> parser) {
        try {
            return parser.parseFrom(value);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }

    private static <T extends MessageLite> T fromBytes(byte[] value, Parser<T> parser) {
        try {
            return parser.parseFrom(value);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }
}
