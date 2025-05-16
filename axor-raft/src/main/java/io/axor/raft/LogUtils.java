package io.axor.raft;

import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;
import io.axor.commons.util.ByteArray;
import io.axor.raft.proto.PeerProto;

import java.io.IOException;
import java.nio.ByteBuffer;

public class LogUtils {
    public static ByteBuffer toBytes(PeerProto.LogId msg, ByteBuffer reuse) throws RaftException {
        reuse.clear();
        reuse.putLong(msg.getIndex()).putLong(msg.getTerm());
        return reuse.flip();
    }

    public static byte[] toBytes(PeerProto.LogId msg) {
        byte[] bytes = new byte[16];
        ByteArray.setLong(bytes, 0, msg.getIndex());
        ByteArray.setLong(bytes, 8, msg.getTerm());
        return msg.toByteArray();
    }

    public static ByteBuffer toBytes(PeerProto.LogValue msg, ByteBuffer reuse) throws RaftException {
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

    public static byte[] toBytes(PeerProto.LogValue msg) {
        return msg.toByteArray();
    }

    public static PeerProto.LogId toId(ByteBuffer value) {
        return PeerProto.LogId.newBuilder()
                .setIndex(value.getLong())
                .setTerm(value.getLong())
                .build();
    }

    public static PeerProto.LogId toId(byte[] value) {
        return PeerProto.LogId.newBuilder()
                .setIndex(ByteArray.getLong(value, 0))
                .setTerm(ByteArray.getLong(value, 8))
                .build();
    }

    public static PeerProto.LogValue toValue(ByteBuffer value) {
        try {
            return PeerProto.LogValue.parseFrom(value);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }

    public static PeerProto.LogValue toValue(byte[] value) {
        try {
            return PeerProto.LogValue.parseFrom(value);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }
}
