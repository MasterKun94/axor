package io.axor.raft.logging;

import com.google.protobuf.ByteString;
import io.axor.raft.proto.PeerProto;
import io.axor.raft.proto.PeerProto.AppendResult;
import io.axor.raft.proto.PeerProto.CommitResult;
import io.axor.raft.proto.PeerProto.LogEntry;
import io.axor.raft.proto.PeerProto.LogId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RaftLoggingTestkit {
    private final RaftLogging logging;
    private final List<LogId> appended = new ArrayList<>();
    private final List<LogId> commited = new ArrayList<>();

    public RaftLoggingTestkit(RaftLogging logging) {
        this.logging = logging;
        logging.addListener(new RaftLogging.LogEntryListener() {
            @Override
            public void appended(LogEntry entry) {
                if (!appended.isEmpty()) {
                    assertEquals(appended.getLast().getIndex() + 1, entry.getId().getIndex());
                    assertTrue(appended.getLast().getTerm() <= entry.getId().getTerm());
                }
                appended.add(entry.getId());
            }

            @Override
            public void removed(LogId id) {
                assertEquals(appended.getLast(), id);
                appended.removeLast();
            }

            @Override
            public void commited(LogId id) {
                if (!commited.isEmpty()) {
                    assertEquals(commited.getLast().getIndex() + 1, id.getIndex());
                    assertTrue(commited.getLast().getTerm() <= id.getTerm());
                }
                commited.add(id);
            }
        });
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
        testListener();
    }

    private LogEntry logEntry(long index, long term, String data) {
        return LogEntry.newBuilder()
                .setId(logEntryId(index, term))
                .setValue(PeerProto.LogValue.newBuilder()
                        .setData(ByteString.copyFromUtf8(data))
                        .setSeqId(index)
                        .setClientId(321)
                        .setTimestamp(123))
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
            assertEquals(entries[i++], logEntry);
        }
        if (entries.length > 1) {
            List<LogEntry> read = logging.read(logEntryId(index, term), false, false, limit, 1024);
            for (int j = 1; j < entries.length; j++) {
                assertEquals(entries[j], read.get(j - 1));
            }
        }
    }

    private void assertEqual(AppendResult.Status status, AppendResult result) {
        assertEquals(status, result.getStatus());
        if (result.getUncommitedList().isEmpty()) {
            assertEquals(logging.commitedId(), logging.logEndId());
        } else {
            assertEquals(logging.logEndId(), result.getUncommitedList().getLast());
        }
    }

    private void assertEqual(CommitResult.Status status, CommitResult result) {
        assertEquals(status, result.getStatus());
        assertEquals(logging.commitedId(), result.getCommited());
    }

    private void append1CommitFailure() throws Exception {
        assertEqual(AppendResult.Status.INDEX_EXPIRED, logging.append(LogId.getDefaultInstance(),
                logEntry(0, 0, "Hello")));
        assertEqual(AppendResult.Status.INDEX_EXCEEDED, logging.append(logEntryId(1, 0),
                logEntry(2, 0, "Hello")));
        assertEquals(RaftLogging.INITIAL_LOG_ID, logging.startedId());
        assertEquals(RaftLogging.INITIAL_LOG_ID, logging.commitedId());
        assertEquals(RaftLogging.INITIAL_LOG_ID, logging.logEndId());
        LogEntry entry = logEntry(1, 0, "Hello");
        logging.append(RaftLogging.INITIAL_LOG_ID, entry);
        testIter(0, 0, 3);
        assertEquals(RaftLogging.INITIAL_LOG_ID, logging.startedId());
        assertEquals(RaftLogging.INITIAL_LOG_ID, logging.commitedId());
        assertEquals(logEntryId(1, 0), logging.logEndId());
        assertEqual(CommitResult.Status.NO_ACTION, logging.commit(logEntryId(0, 0)));
        assertEqual(CommitResult.Status.ILLEGAL_STATE, logging.commit(logEntryId(2, 0)));
    }

    private void append1Commit1() throws Exception {
        LogEntry entry = logEntry(1, 0, "Hello2");
        logging.append(RaftLogging.INITIAL_LOG_ID, entry);
        testIter(0, 0, 3);
        assertEquals(RaftLogging.INITIAL_LOG_ID, logging.startedId());
        assertEquals(RaftLogging.INITIAL_LOG_ID, logging.commitedId());
        assertEquals(logEntryId(1, 0), logging.logEndId());
        assertEqual(CommitResult.Status.SUCCESS, logging.commit(entry.getId()));
        testIter(1, 0, 3, entry);
        assertEquals(logEntryId(1, 0), logging.startedId());
        assertEquals(logEntryId(1, 0), logging.commitedId());
        assertEquals(logEntryId(1, 0), logging.logEndId());
    }

    private void append2CommitFailure() throws Exception {
        assertEquals(logEntryId(1, 0), logging.startedId());
        assertEquals(logEntryId(1, 0), logging.commitedId());
        assertEquals(logEntryId(1, 0), logging.logEndId());
        LogEntry entry1 = logEntry(2, 0, "Hello1");
        LogEntry entry2 = logEntry(3, 0, "Hello2");
        logging.append(logEntryId(1, 0), entry1);
        assertEquals(logEntryId(1, 0), logging.startedId());
        assertEquals(logEntryId(1, 0), logging.commitedId());
        assertEquals(logEntryId(2, 0), logging.logEndId());
        assertEqual(AppendResult.Status.INDEX_EXCEEDED, logging.append(logEntryId(3, 0),
                logEntry(4, 0, "Error")));
        assertEqual(AppendResult.Status.INDEX_EXPIRED, logging.append(logEntryId(0, 0),
                logEntry(1, 0, "Error")));
        logging.append(logEntryId(2, 0), entry2);
        assertEqual(CommitResult.Status.NO_ACTION, logging.commit(logEntryId(1, 0)));
        assertEqual(CommitResult.Status.ILLEGAL_STATE, logging.commit(logEntryId(4, 0)));
    }

    private void append2Commit1() throws Exception {
        LogEntry entry1 = logEntry(2, 0, "Hello1");
        LogEntry entry2 = logEntry(3, 0, "Hello2");
        logging.append(logEntryId(1, 0), Arrays.asList(entry1, entry2));
        assertEquals(logEntryId(1, 0), logging.startedId());
        assertEquals(logEntryId(1, 0), logging.commitedId());
        assertEquals(logEntryId(3, 0), logging.logEndId());
        testIter(2, 0, 1);
        testIter(2, 0, 3);
        assertEqual(CommitResult.Status.SUCCESS, logging.commit(entry1.getId()));
        assertEquals(logEntryId(1, 0), logging.startedId());
        assertEquals(logEntryId(2, 0), logging.commitedId());
        assertEquals(logEntryId(3, 0), logging.logEndId());
        testIter(2, 0, 1, entry1);
        testIter(2, 0, 3, entry1);
        testIter(2, 0, 1, entry1);
        testIter(2, 0, 3, entry1);
        assertEqual(CommitResult.Status.SUCCESS, logging.commit(entry2.getId()));
        assertEquals(logEntryId(1, 0), logging.startedId());
        assertEquals(logEntryId(3, 0), logging.commitedId());
        assertEquals(logEntryId(3, 0), logging.logEndId());
        testIter(2, 0, 1, entry1);
        testIter(2, 0, 3, entry1, entry2);
    }

    private void append2Commit2() throws Exception {
        LogEntry entry1 = logEntry(4, 0, "Hello1");
        LogEntry entry2 = logEntry(5, 0, "Hello2");
        assertEqual(AppendResult.Status.SUCCESS, logging.append(logEntryId(3, 0),
                Arrays.asList(entry1, entry2)));
        assertEquals(logEntryId(3, 0), logging.commitedId());
        assertEquals(logEntryId(5, 0), logging.logEndId());
        assertEqual(CommitResult.Status.SUCCESS, logging.commit(entry2.getId()));
        assertEquals(logEntryId(1, 0), logging.startedId());
        assertEquals(logEntryId(5, 0), logging.commitedId());
        assertEquals(logEntryId(5, 0), logging.logEndId());
        testIter(4, 0, 1, entry1);
        testIter(4, 0, 3, entry1, entry2);
    }

    private void append3Commit3() throws Exception {
        LogEntry entry1 = logEntry(6, 0, "Hello1");
        LogEntry entry2 = logEntry(7, 0, "Hello2");
        LogEntry entry3 = logEntry(8, 0, "Hello3");
        logging.append(logEntryId(5, 0), List.of(entry1, entry2, entry3));
        assertEquals(logEntryId(1, 0), logging.startedId());
        assertEquals(logEntryId(5, 0), logging.commitedId());
        assertEquals(logEntryId(8, 0), logging.logEndId());
        logging.append(logEntryId(5, 0), List.of(entry1));
        assertEquals(logEntryId(5, 0), logging.commitedId());
        assertEquals(logEntryId(6, 0), logging.logEndId());
        logging.append(logEntryId(5, 0), List.of(entry1, entry2));
        logging.append(logEntryId(5, 0), List.of(entry1, entry2, entry3));
        assertEquals(logEntryId(5, 0), logging.commitedId());
        assertEquals(logEntryId(8, 0), logging.logEndId());
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
        AppendResult status = logging.append(logEntryId(5, 0), List.of(entry1, entry2, entry3));
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
        logging.append(logEntryId(8, 0), entry1);
        assertEqual(AppendResult.Status.TERM_EXPIRED, logging.append(logEntryId(9, 2),
                logEntry(10, 1, "Hello2")));
        assertEquals(logEntryId(8, 0), logging.commitedId());
        assertEquals(logEntryId(9, 2), logging.logEndId());
        LogEntry entry2 = logEntry(10, 2, "Hello2");
        logging.append(logEntryId(9, 2), entry2);
        assertEquals(logEntryId(8, 0), logging.commitedId());
        assertEquals(logEntryId(10, 2), logging.logEndId());
        assertEqual(AppendResult.Status.TERM_EXPIRED, logging.append(logEntryId(9, 2),
                logEntry(10, 1, "Hello2")));
        assertEqual(AppendResult.Status.TERM_EXPIRED, logging.append(logEntryId(10, 2),
                logEntry(11, 1, "Hello2")));
        assertEquals(logEntryId(8, 0), logging.commitedId());
        assertEquals(logEntryId(10, 2), logging.logEndId());
        LogEntry entry3 = logEntry(11, 2, "Hello3");
        logging.append(logEntryId(10, 2), List.of(entry3));
        assertEqual(CommitResult.Status.SUCCESS, logging.commit(entry3.getId()));
    }

    private void testUncommited() throws Exception {
        LogEntry entry1 = logEntry(12, 2, "Hello1");
        LogEntry entry2 = logEntry(13, 2, "Hello2");
        LogEntry entry3 = logEntry(14, 3, "Hello3");
        logging.append(logEntryId(11, 2), List.of(entry1, entry2, entry3));
        assertEquals(logEntryId(11, 2), logging.commitedId());
        assertEquals(logEntryId(14, 3), logging.logEndId());
    }

    public void testContinue() throws Exception {
        LogEntry entry01 = logEntry(9, 2, "Hello1");
        LogEntry entry02 = logEntry(10, 2, "Hello2");
        LogEntry entry03 = logEntry(11, 2, "Hello3");
        LogEntry entry1 = logEntry(12, 2, "Hello1");
        LogEntry entry2 = logEntry(13, 2, "Hello2");
        LogEntry entry3 = logEntry(14, 3, "Hello3");
        assertEquals(logEntryId(11, 2), logging.commitedId());
        assertEquals(logEntryId(14, 3), logging.logEndId());
        assertEqual(CommitResult.Status.SUCCESS, logging.commit(logEntryId(14, 3)));
        testIter(9, 0, 1, entry01);
        testIter(9, 0, 2, entry01, entry02);
        testIter(9, 0, 3, entry01, entry02, entry03);
        testIter(9, 0, 7, entry01, entry02, entry03, entry1, entry2, entry3);
    }

    public void testListener() throws Exception {
        assertArrayEquals(appended.toArray(), Arrays.asList(
                logEntryId(1, 0),
                logEntryId(2, 0),
                logEntryId(3, 0),
                logEntryId(4, 0),
                logEntryId(5, 0),
                logEntryId(6, 0),
                logEntryId(7, 0),
                logEntryId(8, 0),
                logEntryId(9, 2),
                logEntryId(10, 2),
                logEntryId(11, 2),
                logEntryId(12, 2),
                logEntryId(13, 2),
                logEntryId(14, 3)).toArray());
        assertArrayEquals(commited.toArray(), Arrays.asList(
                logEntryId(1, 0),
                logEntryId(2, 0),
                logEntryId(3, 0),
                logEntryId(4, 0),
                logEntryId(5, 0),
                logEntryId(6, 0),
                logEntryId(7, 0),
                logEntryId(8, 0),
                logEntryId(9, 2),
                logEntryId(10, 2),
                logEntryId(11, 2)).toArray());
    }
}
