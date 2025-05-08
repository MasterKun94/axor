package io.axor.raft.logging;

import com.google.protobuf.ByteString;
import io.axor.raft.AppendStatus;
import io.axor.raft.CommitStatus;
import io.axor.raft.LogEntry;
import io.axor.raft.LogId;
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
        return new LogEntry(logEntryId(index, term), ByteString.copyFromUtf8(data));
    }

    private LogId logEntryId(long index, long term) {
        return new LogId(index, term);
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

    private void assertEqual(AppendStatus status, AppendResult result) {
        Assert.assertEquals(status, result.status());
        if (result.uncommited().isEmpty()) {
            Assert.assertEquals(logging.commitedId(), logging.logEndId());
        } else {
            Assert.assertEquals(logging.logEndId(), result.uncommited().getLast());
        }
    }

    private void assertEqual(CommitStatus status, CommitResult result) {
        Assert.assertEquals(status, result.status());
        Assert.assertEquals(logging.commitedId(), result.commited());
    }

    private void append1CommitFailure() throws Exception {
        assertEqual(AppendStatus.INDEX_EXPIRED, logging.append(logEntry(0, 0,
                "Hello")));
        assertEqual(AppendStatus.INDEX_EXCEEDED, logging.append(logEntry(2, 0,
                "Hello")));
        Assert.assertEquals(LogId.INITIAL, logging.startedId());
        Assert.assertEquals(LogId.INITIAL, logging.commitedId());
        Assert.assertEquals(LogId.INITIAL, logging.logEndId());
        LogEntry entry = logEntry(1, 0, "Hello");
        logging.append(entry);
        testIter(0, 0, 3);
        Assert.assertEquals(LogId.INITIAL, logging.startedId());
        Assert.assertEquals(LogId.INITIAL, logging.commitedId());
        Assert.assertEquals(logEntryId(1, 0), logging.logEndId());
        assertEqual(CommitStatus.NO_ACTION, logging.commit(logEntryId(0, 0)));
        assertEqual(CommitStatus.ILLEGAL_STATE, logging.commit(logEntryId(2, 0)));
    }

    private void append1Commit1() throws Exception {
        LogEntry entry = logEntry(1, 0, "Hello2");
        logging.append(entry);
        testIter(0, 0, 3);
        Assert.assertEquals(LogId.INITIAL, logging.startedId());
        Assert.assertEquals(LogId.INITIAL, logging.commitedId());
        Assert.assertEquals(logEntryId(1, 0), logging.logEndId());
        assertEqual(CommitStatus.SUCCESS, logging.commit(entry.id()));
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
        assertEqual(AppendStatus.INDEX_EXCEEDED, logging.append(logEntry(4, 0,
                "Error")));
        assertEqual(AppendStatus.INDEX_EXPIRED, logging.append(logEntry(1, 0,
                "Error")));
        logging.append(entry2);
        assertEqual(CommitStatus.NO_ACTION, logging.commit(logEntryId(1, 0)));
        assertEqual(CommitStatus.ILLEGAL_STATE, logging.commit(logEntryId(4, 0)));
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
        assertEqual(CommitStatus.SUCCESS, logging.commit(entry1.id()));
        Assert.assertEquals(logEntryId(1, 0), logging.startedId());
        Assert.assertEquals(logEntryId(2, 0), logging.commitedId());
        Assert.assertEquals(logEntryId(3, 0), logging.logEndId());
        testIter(2, 0, 1, entry1);
        testIter(2, 0, 3, entry1);
        testIter(2, 0, 1, entry1);
        testIter(2, 0, 3, entry1);
        assertEqual(CommitStatus.SUCCESS, logging.commit(entry2.id()));
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
        assertEqual(CommitStatus.SUCCESS, logging.commit(entry2.id()));
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
        assertEqual(CommitStatus.SUCCESS, logging.commit(entry3.id()));
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
        assertEqual(AppendStatus.NO_ACTION, status);
    }


    private void testCommitNoAction() throws Exception {
        assertEqual(CommitStatus.NO_ACTION, logging.commit(new LogId(6, 0)));
        assertEqual(CommitStatus.NO_ACTION, logging.commit(new LogId(7, 0)));
        assertEqual(CommitStatus.NO_ACTION, logging.commit(new LogId(8, 0)));
    }

    private void testExpire() throws Exception {
        LogEntry entry2 = logEntry(7, 0, "Hello2");
        LogEntry entry3 = logEntry(8, 0, "Hello3");
        logging.expire(entry2.id());
        testIter(7, 0, 1, entry2);
        testIter(7, 0, 2, entry2, entry3);
        testIter(7, 0, 3, entry2, entry3);
    }

    private void testTerm() throws Exception {

        LogEntry entry1 = logEntry(9, 2, "Hello1");
        logging.append(entry1);
        assertEqual(AppendStatus.TERM_EXPIRED, logging.append(logEntry(10, 1, "Hello2")));
        Assert.assertEquals(logEntryId(8, 0), logging.commitedId());
        Assert.assertEquals(logEntryId(9, 2), logging.logEndId());
        LogEntry entry2 = logEntry(10, 2, "Hello2");
        logging.append(entry2);
        Assert.assertEquals(logEntryId(8, 0), logging.commitedId());
        Assert.assertEquals(logEntryId(10, 2), logging.logEndId());
        assertEqual(AppendStatus.TERM_EXPIRED, logging.append(logEntry(10, 1, "Hello2")));
        assertEqual(AppendStatus.TERM_EXPIRED, logging.append(logEntry(11, 1, "Hello2")));
        Assert.assertEquals(logEntryId(8, 0), logging.commitedId());
        Assert.assertEquals(logEntryId(10, 2), logging.logEndId());
        LogEntry entry3 = logEntry(11, 2, "Hello3");
        logging.append(List.of(entry3));
        assertEqual(CommitStatus.SUCCESS, logging.commit(entry3.id()));
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
        assertEqual(CommitStatus.SUCCESS, logging.commit(logEntryId(14, 3)));
        testIter(9, 0, 1, entry01);
        testIter(9, 0, 2, entry01, entry02);
        testIter(9, 0, 3, entry01, entry02, entry03);
        testIter(9, 0, 7, entry01, entry02, entry03, entry1, entry2, entry3);
    }
}
