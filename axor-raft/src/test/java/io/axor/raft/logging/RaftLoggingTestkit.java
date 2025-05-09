package io.axor.raft.logging;

import com.google.protobuf.ByteString;
import io.axor.raft.proto.PeerProto;
import io.axor.raft.proto.PeerProto.AppendResult;
import io.axor.raft.proto.PeerProto.CommitResult;
import io.axor.raft.proto.PeerProto.LogEntry;
import io.axor.raft.proto.PeerProto.LogId;
import org.junit.Assert;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertTrue;

public class RaftLoggingTestkit {
    private final RaftLogging logging;

    public RaftLoggingTestkit(RaftLogging logging) {
        this.logging = logging;
    }

    public void test() throws Exception {
        append1CommitFailure();
        append1Commit1();
        append2CommitFailure();
        append2Commit1();
        append2Commit2();
        append3Commit3();
        testAppendNoAction();
        testCommitNoAction();
        testExpire();
        testTerm();
        testUncommited();
    }

    private LogEntry logEntry(long index, long term, String data) {
        return LogEntry.newBuilder()
                .setId(logEntryId(index, term))
                .setValue(PeerProto.LogValue.newBuilder()
                        .setData(ByteString.copyFromUtf8(data))
                        .setClientTxnId(index)
                        .setClientId(ByteString.copyFromUtf8("test")))
                .build();
    }

    private LogId logEntryId(long index, long term) {
        return LogId.newBuilder().setIndex(index).setTerm(term).build();
    }

    private void testIter(int index, int term, int limit, LogEntry... entries) throws Exception {
        if (entries.length == 0) {
            assertTrue(logging.read(logEntryId(index, term), true, false, limit, 1024).isEmpty());
            assertTrue(logging.read(logEntryId(index, term), false, false, limit, 1024).isEmpty());
            return;
        }
        int i = 0;
        for (LogEntry logEntry : logging.read(logEntryId(index, term), true, false, limit, 1024)) {
            Assert.assertEquals(entries[i++], logEntry);
        }
        if (entries.length > 1) {
            List<LogEntry> read = logging.read(logEntryId(index, term), false, false, limit, 1024);
            for (int j = 1; j < entries.length; j++) {
                Assert.assertEquals(entries[j], read.get(j - 1));
            }
        }
    }

    private void assertEqual(AppendResult.Status status, AppendResult result) {
        Assert.assertEquals(status, result.getStatus());
        if (result.getUncommitedList().isEmpty()) {
            Assert.assertEquals(logging.commitedId(), logging.logEndId());
        } else {
            Assert.assertEquals(logging.logEndId(), result.getUncommitedList().getLast());
        }
    }

    private void assertEqual(CommitResult.Status status, CommitResult result) {
        Assert.assertEquals(status, result.getStatus());
        Assert.assertEquals(logging.commitedId(), result.getCommited());
    }

    private void append1CommitFailure() throws Exception {
        assertEqual(AppendResult.Status.INDEX_EXPIRED, logging.append(logEntry(0, 0,
                "Hello")));
        assertEqual(AppendResult.Status.INDEX_EXCEEDED, logging.append(logEntry(2, 0,
                "Hello")));
        Assert.assertEquals(RaftLogging.INITIAL_LOG_ID, logging.startedId());
        Assert.assertEquals(RaftLogging.INITIAL_LOG_ID, logging.commitedId());
        Assert.assertEquals(RaftLogging.INITIAL_LOG_ID, logging.logEndId());
        LogEntry entry = logEntry(1, 0, "Hello");
        logging.append(entry);
        testIter(0, 0, 3);
        Assert.assertEquals(RaftLogging.INITIAL_LOG_ID, logging.startedId());
        Assert.assertEquals(RaftLogging.INITIAL_LOG_ID, logging.commitedId());
        Assert.assertEquals(logEntryId(1, 0), logging.logEndId());
        assertEqual(CommitResult.Status.NO_ACTION, logging.commit(logEntryId(0, 0)));
        assertEqual(CommitResult.Status.ILLEGAL_STATE, logging.commit(logEntryId(2, 0)));
    }

    private void append1Commit1() throws Exception {
        LogEntry entry = logEntry(1, 0, "Hello2");
        logging.append(entry);
        testIter(0, 0, 3);
        Assert.assertEquals(RaftLogging.INITIAL_LOG_ID, logging.startedId());
        Assert.assertEquals(RaftLogging.INITIAL_LOG_ID, logging.commitedId());
        Assert.assertEquals(logEntryId(1, 0), logging.logEndId());
        assertEqual(CommitResult.Status.SUCCESS, logging.commit(entry.getId()));
        testIter(1, 0, 3, entry);
        Assert.assertEquals(logEntryId(1, 0), logging.startedId());
        Assert.assertEquals(logEntryId(1, 0), logging.commitedId());
        Assert.assertEquals(logEntryId(1, 0), logging.logEndId());
    }

    private void append2CommitFailure() throws Exception {
        Assert.assertEquals(logEntryId(1, 0), logging.startedId());
        Assert.assertEquals(logEntryId(1, 0), logging.commitedId());
        Assert.assertEquals(logEntryId(1, 0), logging.logEndId());
        LogEntry entry1 = logEntry(2, 0, "Hello1");
        LogEntry entry2 = logEntry(3, 0, "Hello2");
        logging.append(entry1);
        Assert.assertEquals(logEntryId(1, 0), logging.startedId());
        Assert.assertEquals(logEntryId(1, 0), logging.commitedId());
        Assert.assertEquals(logEntryId(2, 0), logging.logEndId());
        assertEqual(AppendResult.Status.INDEX_EXCEEDED, logging.append(logEntry(4, 0,
                "Error")));
        assertEqual(AppendResult.Status.INDEX_EXPIRED, logging.append(logEntry(1, 0,
                "Error")));
        logging.append(entry2);
        assertEqual(CommitResult.Status.NO_ACTION, logging.commit(logEntryId(1, 0)));
        assertEqual(CommitResult.Status.ILLEGAL_STATE, logging.commit(logEntryId(4, 0)));
    }

    private void append2Commit1() throws Exception {
        LogEntry entry1 = logEntry(2, 0, "Hello1");
        LogEntry entry2 = logEntry(3, 0, "Hello2");
        logging.append(Arrays.asList(entry1, entry2));
        Assert.assertEquals(logEntryId(1, 0), logging.startedId());
        Assert.assertEquals(logEntryId(1, 0), logging.commitedId());
        Assert.assertEquals(logEntryId(3, 0), logging.logEndId());
        testIter(2, 0, 1);
        testIter(2, 0, 3);
        assertEqual(CommitResult.Status.SUCCESS, logging.commit(entry1.getId()));
        Assert.assertEquals(logEntryId(1, 0), logging.startedId());
        Assert.assertEquals(logEntryId(2, 0), logging.commitedId());
        Assert.assertEquals(logEntryId(3, 0), logging.logEndId());
        testIter(2, 0, 1, entry1);
        testIter(2, 0, 3, entry1);
        testIter(2, 0, 1, entry1);
        testIter(2, 0, 3, entry1);
        assertEqual(CommitResult.Status.SUCCESS, logging.commit(entry2.getId()));
        Assert.assertEquals(logEntryId(1, 0), logging.startedId());
        Assert.assertEquals(logEntryId(3, 0), logging.commitedId());
        Assert.assertEquals(logEntryId(3, 0), logging.logEndId());
        testIter(2, 0, 1, entry1);
        testIter(2, 0, 3, entry1, entry2);
    }

    private void append2Commit2() throws Exception {
        LogEntry entry1 = logEntry(4, 0, "Hello1");
        LogEntry entry2 = logEntry(5, 0, "Hello2");
        logging.append(Arrays.asList(entry1, entry2));
        Assert.assertEquals(logEntryId(3, 0), logging.commitedId());
        Assert.assertEquals(logEntryId(5, 0), logging.logEndId());
        assertEqual(CommitResult.Status.SUCCESS, logging.commit(entry2.getId()));
        Assert.assertEquals(logEntryId(1, 0), logging.startedId());
        Assert.assertEquals(logEntryId(5, 0), logging.commitedId());
        Assert.assertEquals(logEntryId(5, 0), logging.logEndId());
        testIter(4, 0, 1, entry1);
        testIter(4, 0, 3, entry1, entry2);
    }

    private void append3Commit3() throws Exception {
        LogEntry entry1 = logEntry(6, 0, "Hello1");
        LogEntry entry2 = logEntry(7, 0, "Hello2");
        LogEntry entry3 = logEntry(8, 0, "Hello3");
        logging.append(List.of(entry1, entry2, entry3));
        Assert.assertEquals(logEntryId(1, 0), logging.startedId());
        Assert.assertEquals(logEntryId(5, 0), logging.commitedId());
        Assert.assertEquals(logEntryId(8, 0), logging.logEndId());
        logging.append(List.of(entry1));
        Assert.assertEquals(logEntryId(5, 0), logging.commitedId());
        Assert.assertEquals(logEntryId(6, 0), logging.logEndId());
        logging.append(List.of(entry1, entry2));
        logging.append(List.of(entry1, entry2, entry3));
        Assert.assertEquals(logEntryId(5, 0), logging.commitedId());
        Assert.assertEquals(logEntryId(8, 0), logging.logEndId());
        assertEqual(CommitResult.Status.SUCCESS, logging.commit(entry3.getId()));
        testIter(6, 0, 1, entry1);
        testIter(6, 0, 2, entry1, entry2);
        testIter(6, 0, 3, entry1, entry2, entry3);
        testIter(6, 0, 4, entry1, entry2, entry3);
    }

    private void testAppendNoAction() throws Exception {
        LogEntry entry1 = logEntry(6, 0, "Hello1");
        LogEntry entry2 = logEntry(7, 0, "Hello2");
        LogEntry entry3 = logEntry(8, 0, "Hello3");
        AppendResult status = logging.append(List.of(entry1, entry2, entry3));
        assertEqual(AppendResult.Status.NO_ACTION, status);
    }


    private void testCommitNoAction() throws Exception {
        assertEqual(CommitResult.Status.NO_ACTION, logging.commit(logEntryId(6, 0)));
        assertEqual(CommitResult.Status.NO_ACTION, logging.commit(logEntryId(7, 0)));
        assertEqual(CommitResult.Status.NO_ACTION, logging.commit(logEntryId(8, 0)));
    }

    private void testExpire() throws Exception {
        LogEntry entry2 = logEntry(7, 0, "Hello2");
        LogEntry entry3 = logEntry(8, 0, "Hello3");
        logging.expire(entry2.getId());
        testIter(7, 0, 1, entry2);
        testIter(7, 0, 2, entry2, entry3);
        testIter(7, 0, 3, entry2, entry3);
    }

    private void testTerm() throws Exception {

        LogEntry entry1 = logEntry(9, 2, "Hello1");
        logging.append(entry1);
        assertEqual(AppendResult.Status.TERM_EXPIRED, logging.append(logEntry(10, 1, "Hello2")));
        Assert.assertEquals(logEntryId(8, 0), logging.commitedId());
        Assert.assertEquals(logEntryId(9, 2), logging.logEndId());
        LogEntry entry2 = logEntry(10, 2, "Hello2");
        logging.append(entry2);
        Assert.assertEquals(logEntryId(8, 0), logging.commitedId());
        Assert.assertEquals(logEntryId(10, 2), logging.logEndId());
        assertEqual(AppendResult.Status.TERM_EXPIRED, logging.append(logEntry(10, 1, "Hello2")));
        assertEqual(AppendResult.Status.TERM_EXPIRED, logging.append(logEntry(11, 1, "Hello2")));
        Assert.assertEquals(logEntryId(8, 0), logging.commitedId());
        Assert.assertEquals(logEntryId(10, 2), logging.logEndId());
        LogEntry entry3 = logEntry(11, 2, "Hello3");
        logging.append(List.of(entry3));
        assertEqual(CommitResult.Status.SUCCESS, logging.commit(entry3.getId()));
    }

    private void testUncommited() throws Exception {
        LogEntry entry1 = logEntry(12, 2, "Hello1");
        LogEntry entry2 = logEntry(13, 2, "Hello2");
        LogEntry entry3 = logEntry(14, 3, "Hello3");
        logging.append(List.of(entry1, entry2, entry3));
        Assert.assertEquals(logEntryId(11, 2), logging.commitedId());
        Assert.assertEquals(logEntryId(14, 3), logging.logEndId());
    }

    public void testContinue() throws Exception {
        LogEntry entry01 = logEntry(9, 2, "Hello1");
        LogEntry entry02 = logEntry(10, 2, "Hello2");
        LogEntry entry03 = logEntry(11, 2, "Hello3");
        LogEntry entry1 = logEntry(12, 2, "Hello1");
        LogEntry entry2 = logEntry(13, 2, "Hello2");
        LogEntry entry3 = logEntry(14, 3, "Hello3");
        Assert.assertEquals(logEntryId(11, 2), logging.commitedId());
        Assert.assertEquals(logEntryId(14, 3), logging.logEndId());
        assertEqual(CommitResult.Status.SUCCESS, logging.commit(logEntryId(14, 3)));
        testIter(9, 0, 1, entry01);
        testIter(9, 0, 2, entry01, entry02);
        testIter(9, 0, 3, entry01, entry02, entry03);
        testIter(9, 0, 7, entry01, entry02, entry03, entry1, entry2, entry3);
    }
}
