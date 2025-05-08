package io.axor.raft.logging;

import io.axor.commons.util.ByteArray;
import io.axor.raft.LogEntry;
import io.axor.raft.LogId;

import java.nio.ByteBuffer;

public class LogUtils {
    public static byte[] toBytes(LogId id) {
        byte[] b = new byte[16];
        ByteArray.setLong(b, 0, id.index());
        ByteArray.setLong(b, 8, id.term());
        return b;
    }

    public static LogId toId(byte[] b) {
        return new LogId(ByteArray.getLong(b, 0), ByteArray.getLong(b, 8));
    }

    public static ByteBuffer toBytes(LogId id, ByteBuffer reuse) {
        int pos = reuse.position();
        reuse.putLong(id.index());
        reuse.putLong(id.term());
        return reuse.slice(pos, 16);
    }

    public static LogId toId(ByteBuffer buffer) {
        return new LogId(buffer.getLong(), buffer.getLong());
    }

    public static ByteBuffer dataBytes(LogEntry entry, ByteBuffer reuse) {
        ByteBuffer data = entry.data().asReadOnlyByteBuffer();
        if (data.isDirect() == reuse.isDirect()) {
            return data;
        } else {
            int pos = reuse.position();
            reuse.put(data);
            return reuse.slice(pos, reuse.position() - pos);
        }
    }
}
