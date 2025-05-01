package io.axor.cp.raft;

import io.axor.commons.util.ByteArray;
import io.axor.cp.messages.LogEntry;
import io.axor.cp.messages.LogEntryId;

import java.nio.ByteBuffer;

public class LogUtils {
    public static byte[] toBytes(LogEntryId id) {
        byte[] b = new byte[16];
        ByteArray.setLong(b, 0, id.index());
        ByteArray.setLong(b, 8, id.term());
        return b;
    }

    public static LogEntryId toId(byte[] b) {
        return new LogEntryId(ByteArray.getLong(b, 0), ByteArray.getLong(b, 8));
    }

    public static ByteBuffer toBytes(LogEntryId id, ByteBuffer reuse) {
        int pos = reuse.position();
        reuse.putLong(id.index());
        reuse.putLong(id.term());
        return reuse.slice(pos, 16);
    }

    public static LogEntryId toId(ByteBuffer buffer) {
        return new LogEntryId(buffer.getLong(), buffer.getLong());
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
