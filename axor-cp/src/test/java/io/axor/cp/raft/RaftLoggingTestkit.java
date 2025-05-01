package io.axor.cp.raft;

import com.google.protobuf.ByteString;
import io.axor.cp.messages.AppendStatus;
import io.axor.cp.messages.CommitStatus;
import io.axor.cp.messages.LogEntry;
import io.axor.cp.messages.LogEntryId;
import io.axor.cp.raft.logging.RaftLogging;
import org.junit.Assert;

import java.util.Arrays;
import java.util.List;

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
        testExpire();
        testTerm();
        testUncommited();
    }

    private LogEntry logEntry(long index, long term, String data) {
        return new LogEntry(logEntryId(index, term), ByteString.copyFromUtf8(data));
    }

    private LogEntryId logEntryId(long index, long term) {
        return new LogEntryId(index, term);
    }

    private void testIter(int index, int term, int limit, LogEntry... entries) throws Exception {
        int i = 0;
        for (LogEntry logEntry : logging.read(logEntryId(index, term), limit, 1024)) {
            Assert.assertEquals(entries[i++], logEntry);
        }
    }

    private void append1CommitFailure() throws Exception {
        Assert.assertEquals(AppendStatus.INDEX_EXPIRED, logging.append(logEntry(0, 0,
                "Hello")));
        Assert.assertEquals(AppendStatus.INDEX_EXCEEDED, logging.append(logEntry(2, 0,
                "Hello")));
        Assert.assertEquals(LogEntryId.INITIAL, logging.startedId());
        Assert.assertEquals(LogEntryId.INITIAL, logging.commitedId());
        Assert.assertEquals(LogEntryId.INITIAL, logging.uncommitedId());
        LogEntry entry = logEntry(1, 0, "Hello");
        logging.append(entry);
        testIter(0, 0, 3);
        Assert.assertEquals(LogEntryId.INITIAL, logging.startedId());
        Assert.assertEquals(LogEntryId.INITIAL, logging.commitedId());
        Assert.assertEquals(logEntryId(1, 0), logging.uncommitedId());
        Assert.assertEquals(CommitStatus.ILLEGAL_STATE, logging.commit(logEntryId(0, 0)));
        Assert.assertEquals(CommitStatus.ILLEGAL_STATE, logging.commit(logEntryId(2, 0)));
    }

    private void append1Commit1() throws Exception {
        LogEntry entry = logEntry(1, 0, "Hello2");
        logging.append(entry);
        testIter(0, 0, 3);
        Assert.assertEquals(LogEntryId.INITIAL, logging.startedId());
        Assert.assertEquals(LogEntryId.INITIAL, logging.commitedId());
        Assert.assertEquals(logEntryId(1, 0), logging.uncommitedId());
        Assert.assertEquals(CommitStatus.SUCCESS, logging.commit(entry.id()));
        testIter(1, 0, 3, entry);
        Assert.assertEquals(logEntryId(1, 0), logging.startedId());
        Assert.assertEquals(logEntryId(1, 0), logging.commitedId());
        Assert.assertEquals(logEntryId(1, 0), logging.uncommitedId());
    }

    private void append2CommitFailure() throws Exception {
        Assert.assertEquals(logEntryId(1, 0), logging.startedId());
        Assert.assertEquals(logEntryId(1, 0), logging.commitedId());
        Assert.assertEquals(logEntryId(1, 0), logging.uncommitedId());
        LogEntry entry1 = logEntry(2, 0, "Hello1");
        LogEntry entry2 = logEntry(3, 0, "Hello2");
        logging.append(entry1);
        Assert.assertEquals(logEntryId(1, 0), logging.startedId());
        Assert.assertEquals(logEntryId(1, 0), logging.commitedId());
        Assert.assertEquals(logEntryId(2, 0), logging.uncommitedId());
        Assert.assertEquals(AppendStatus.INDEX_EXCEEDED, logging.append(logEntry(4, 0,
                "Error")));
        Assert.assertEquals(AppendStatus.INDEX_EXPIRED, logging.append(logEntry(1, 0,
                "Error")));
        logging.append(entry2);
        Assert.assertEquals(CommitStatus.ILLEGAL_STATE, logging.commit(logEntryId(1, 0)));
        Assert.assertEquals(CommitStatus.ILLEGAL_STATE, logging.commit(logEntryId(4, 0)));
    }

    private void append2Commit1() throws Exception {
        LogEntry entry1 = logEntry(2, 0, "Hello1");
        LogEntry entry2 = logEntry(3, 0, "Hello2");
        logging.append(Arrays.asList(entry1, entry2));
        Assert.assertEquals(logEntryId(1, 0), logging.startedId());
        Assert.assertEquals(logEntryId(1, 0), logging.commitedId());
        Assert.assertEquals(logEntryId(3, 0), logging.uncommitedId());
        testIter(2, 0, 1);
        testIter(2, 0, 3);
        Assert.assertEquals(CommitStatus.SUCCESS, logging.commit(entry1.id()));
        Assert.assertEquals(logEntryId(1, 0), logging.startedId());
        Assert.assertEquals(logEntryId(2, 0), logging.commitedId());
        Assert.assertEquals(logEntryId(3, 0), logging.uncommitedId());
        testIter(2, 0, 1, entry1);
        testIter(2, 0, 3, entry1);
        testIter(2, 0, 1, entry1);
        testIter(2, 0, 3, entry1);
        Assert.assertEquals(CommitStatus.SUCCESS, logging.commit(entry2.id()));
        Assert.assertEquals(logEntryId(1, 0), logging.startedId());
        Assert.assertEquals(logEntryId(3, 0), logging.commitedId());
        Assert.assertEquals(logEntryId(3, 0), logging.uncommitedId());
        testIter(2, 0, 1, entry1);
        testIter(2, 0, 3, entry1, entry2);
    }

    private void append2Commit2() throws Exception {
        LogEntry entry1 = logEntry(4, 0, "Hello1");
        LogEntry entry2 = logEntry(5, 0, "Hello2");
        logging.append(Arrays.asList(entry1, entry2));
        Assert.assertEquals(logEntryId(3, 0), logging.commitedId());
        Assert.assertEquals(logEntryId(5, 0), logging.uncommitedId());
        Assert.assertEquals(CommitStatus.SUCCESS, logging.commit(entry2.id()));
        Assert.assertEquals(logEntryId(1, 0), logging.startedId());
        Assert.assertEquals(logEntryId(5, 0), logging.commitedId());
        Assert.assertEquals(logEntryId(5, 0), logging.uncommitedId());
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
        Assert.assertEquals(logEntryId(8, 0), logging.uncommitedId());
        logging.append(List.of(entry1));
        Assert.assertEquals(logEntryId(5, 0), logging.commitedId());
        Assert.assertEquals(logEntryId(6, 0), logging.uncommitedId());
        logging.append(List.of(entry1, entry2));
        logging.append(List.of(entry1, entry2, entry3));
        Assert.assertEquals(logEntryId(5, 0), logging.commitedId());
        Assert.assertEquals(logEntryId(8, 0), logging.uncommitedId());
        Assert.assertEquals(CommitStatus.SUCCESS, logging.commit(entry3.id()));
        testIter(6, 0, 1, entry1);
        testIter(6, 0, 2, entry1, entry2);
        testIter(6, 0, 3, entry1, entry2, entry3);
        testIter(6, 0, 4, entry1, entry2, entry3);
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
        Assert.assertEquals(AppendStatus.TERM_EXPIRED, logging.append(logEntry(10, 1, "Hello2")));
        Assert.assertEquals(logEntryId(8, 0), logging.commitedId());
        Assert.assertEquals(logEntryId(9, 2), logging.uncommitedId());
        LogEntry entry2 = logEntry(10, 2, "Hello2");
        logging.append(entry2);
        Assert.assertEquals(logEntryId(8, 0), logging.commitedId());
        Assert.assertEquals(logEntryId(10, 2), logging.uncommitedId());
        Assert.assertEquals(AppendStatus.TERM_EXPIRED, logging.append(logEntry(10, 1, "Hello2")));
        Assert.assertEquals(AppendStatus.TERM_EXPIRED, logging.append(logEntry(11, 1, "Hello2")));
        Assert.assertEquals(logEntryId(8, 0), logging.commitedId());
        Assert.assertEquals(logEntryId(10, 2), logging.uncommitedId());
        LogEntry entry3 = logEntry(11, 2, "Hello3");
        logging.append(List.of(entry3));
        Assert.assertEquals(CommitStatus.SUCCESS, logging.commit(entry3.id()));
    }

    private void testUncommited() throws Exception {
        LogEntry entry1 = logEntry(12, 2, "Hello1");
        LogEntry entry2 = logEntry(13, 2, "Hello2");
        LogEntry entry3 = logEntry(14, 3, "Hello3");
        logging.append(List.of(entry1, entry2, entry3));
        Assert.assertEquals(logEntryId(11, 2), logging.commitedId());
        Assert.assertEquals(logEntryId(14, 3), logging.uncommitedId());
    }

    public void testContinue() throws Exception {
        LogEntry entry01 = logEntry(9, 2, "Hello1");
        LogEntry entry02 = logEntry(10, 2, "Hello2");
        LogEntry entry03 = logEntry(11, 2, "Hello3");
        LogEntry entry1 = logEntry(12, 2, "Hello1");
        LogEntry entry2 = logEntry(13, 2, "Hello2");
        LogEntry entry3 = logEntry(14, 3, "Hello3");
        Assert.assertEquals(logEntryId(11, 2), logging.commitedId());
        Assert.assertEquals(logEntryId(14, 3), logging.uncommitedId());
        Assert.assertEquals(CommitStatus.SUCCESS, logging.commit(logEntryId(14, 3)));
        testIter(9, 0, 1, entry01);
        testIter(9, 0, 2, entry01, entry02);
        testIter(9, 0, 3, entry01, entry02, entry03);
        testIter(9, 0, 7, entry01, entry02, entry03, entry1, entry2, entry3);
    }
}
