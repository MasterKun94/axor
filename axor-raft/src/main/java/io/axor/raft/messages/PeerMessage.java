package io.axor.raft.messages;

import com.google.protobuf.ByteString;
import io.axor.raft.AppendStatus;
import io.axor.raft.LogEntry;
import io.axor.raft.LogId;

import java.util.List;

public sealed interface PeerMessage {

    record ClientTxnReq(long txnId, ByteString data) implements PeerMessage {
    }

    record LogAppend(long txnId, long term, List<LogEntry> entries,
                     LogId leaderCommited) implements PeerMessage {
    }

    record LogAppendAck(long txnId, long term, AppendStatus status,
                        LogId commited, List<LogId> uncommited) implements PeerMessage {
        public boolean success() {
            return status.isSuccess();
        }

        public LogId logEndId() {
            return uncommited == null || uncommited.isEmpty() ? commited : uncommited.getLast();
        }
    }

    record LeaderHeartbeat(long term, LogId leaderCommited) implements PeerMessage {
    }

    record LogFetch(long txnId, LogId startAt, boolean includeStartAt,
                    int limit) implements PeerMessage {
        public LogFetch(long txnId, LogId startAtExclude, int limit) {
            this(txnId, startAtExclude, false, limit);
        }
    }

    record LogFetchRes(long txnId, boolean success, List<LogEntry> entries,
                       String errorMsg, LogId currentCommited) implements PeerMessage {
        public LogFetchRes(long txnId, List<LogEntry> entries, LogId currentCommited) {
            this(txnId, true, entries, "OK", currentCommited);
        }

        public LogFetchRes(long txnId, String errorMsg, LogId currentCommited) {
            this(txnId, false, null, errorMsg, currentCommited);
        }
    }

    record RequestVote(long txnId, long term, LogId logEndId) implements PeerMessage {
    }

    record RequestVoteAck(long txnId, long term, boolean voteGranted) implements PeerMessage {
    }
}
