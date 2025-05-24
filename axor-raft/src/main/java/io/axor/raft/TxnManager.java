package io.axor.raft;

import com.google.protobuf.Message;
import io.axor.api.ActorRef;
import io.axor.api.MessageUtils;
import io.axor.commons.concurrent.EventExecutor;
import io.axor.commons.concurrent.EventStage;
import io.axor.raft.logging.RaftLogging;
import io.axor.raft.proto.PeerProto;
import io.axor.raft.proto.PeerProto.MediatorMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class TxnManager {
    private static final Logger LOG = LoggerFactory.getLogger(TxnManager.class);

    private final RaftContext raftContext;
    private final Map<Key, Value> table = new HashMap<>();
    private final Map<PeerProto.LogId, Key> idIndex = new HashMap<>();
    private final ScheduledFuture<?> expireCheckSchedule;

    public TxnManager(RaftContext raftContext) {
        this.raftContext = raftContext;
        this.expireCheckSchedule = raftContext.getContext().scheduler().schedule(() -> {
            long expireTimeout = raftContext.getConfig().clientTxnCacheCheckTimeout().toMillis();
            long current = System.currentTimeMillis();
            Iterator<Map.Entry<Key, Value>> iter = table.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<Key, Value> entry = iter.next();
                if (current - entry.getValue().getCreateTime() > expireTimeout) {
                    LOG.info("Remove TxnEntry({}, {})", entry.getKey(), entry.getValue());
                    iter.remove();
                }
            }
        }, raftContext.getConfig().clientTxnCacheCheckInterval().toMillis(), TimeUnit.MILLISECONDS);
    }

    public RaftLogging.LogEntryListener getListener() {
        return new RaftLogging.LogEntryListener() {
            @Override
            public void appended(PeerProto.LogEntry entry) {
                PeerProto.LogValue entryValue = entry.getValue();
                Key key = new Key(entryValue.getClientId(), entryValue.getSeqId());
                Value value = table.get(key);
                if (value == null) {
                    value = new Value(entryValue.getSeqId(), entryValue.getTimestamp());
                    table.put(key, value);
                } else {
                    assert value.getStatus() == TxnStatus.WAITING;
                    assert value.getCreateTime() == entryValue.getTimestamp();
                }
                value.setStatus(TxnStatus.APPLIED);
                value.setAckedSeqId(entryValue.getAckedSeqIdList());
                idIndex.put(entry.getId(), key);
            }

            @Override
            public void removed(PeerProto.LogId id) {
                Key key = idIndex.remove(id);
                if (key == null) {
                    return;
                }
                Value value = Objects.requireNonNull(table.remove(key));
                if (value.hasListener() &&
                    raftContext.getRaftState().getPeerState() == PeerState.LEADER) {
                    LOG.warn("Listener of {} is not empty while removed",
                            MessageUtils.loggable(id));
                }
            }

            @Override
            public void commited(PeerProto.LogId id) {
                Key key = Objects.requireNonNull(idIndex.get(id));
                Value value = Objects.requireNonNull(table.get(key));
                assert value.getStatus() == TxnStatus.APPLIED;
                value.setStatus(TxnStatus.COMMITED);
                value.fireAndClearListener(MediatorMessage.TxnRes.newBuilder()
                        .setCommitedId(id)
                        .setTerm(raftContext.getRaftState().getCurrentTerm())
                        .build());
                for (long l : value.getAckedSeqId()) {
                    Value remove = table.remove(new Key(key.clientId, l));
                    if (remove == null) {
                        LOG.warn("Empty remove by Key[{}, {}]", key.clientId, l);
                    } else {
                        idIndex.remove(Objects.requireNonNull(remove.getAppliedLogId()), key);
                    }
                }
            }

            @Override
            public void loadSnapshot(PeerProto.Snapshot snapshot) {
                table.clear();
                for (var unfinishedTxn : snapshot.getUnfinishedTxnList()) {
                    Key key = new Key(unfinishedTxn.getClientId(), unfinishedTxn.getSeqId());
                    Value value = new Value(key.seqId, unfinishedTxn.getCreateTime());
                    value.setAppliedLogId(unfinishedTxn.getAppliedLogId());
                    value.setAckedSeqId(unfinishedTxn.getAckedSeqIdList());
                    value.setStatus(TxnStatus.COMMITED);
                    table.put(key, value);
                }
            }

            @Override
            public void installSnapshot(PeerProto.InstallSnapshot snapshot) {
                loadSnapshot(snapshot.getSnapshot());
            }

            @Override
            public EventStage<PeerProto.Snapshot> takeSnapshot(PeerProto.Snapshot snapshot,
                                                               EventExecutor executor) {
                PeerProto.Snapshot.Builder builder = snapshot.toBuilder();
                for (Map.Entry<Key, Value> entry : table.entrySet()) {
                    Key key = entry.getKey();
                    Value value = entry.getValue();
                    if (value.getStatus() != TxnStatus.COMMITED) {
                        continue;
                    }
                    builder.addUnfinishedTxn(PeerProto.Snapshot.UnfinishedTxn.newBuilder()
                            .setClientId(key.clientId)
                            .setSeqId(key.seqId)
                            .setCreateTime(value.createTime)
                            .setAppliedLogId(value.getAppliedLogId())
                            .addAllAckedSeqId(value.ackedSeqId));
                }
                return EventStage.succeed(builder.build(), executor);
            }
        };
    }

    public boolean inTxnOrCommited(Key key) {
        Value value = table.get(key);
        return value != null && (value.isInTxn() || value.getStatus() == TxnStatus.COMMITED);
    }

    public boolean isCommited(Key key) {
        Value value = table.get(key);
        return value != null && value.getStatus() == TxnStatus.COMMITED;
    }

    public boolean isWaiting(Key key) {
        Value value = table.get(key);
        return value != null && value.getStatus() == TxnStatus.WAITING;
    }

    public long getCreateTime(Key key) {
        return Objects.requireNonNull(table.get(key)).getCreateTime();
    }

    public void addClient(Key key, ActorRef<MediatorMessage> listener, int retryNum) {
        Value value = Objects.requireNonNull(table.get(key));
        if (value.getStatus() == TxnStatus.COMMITED) {
            listener.tell(MediatorMessage.newBuilder()
                    .setSeqId(key.seqId)
                    .setRetryNum(retryNum)
                    .setTxnRes(MediatorMessage.TxnRes.newBuilder()
                            .setCommitedId(value.appliedLogId)
                            .setTerm(raftContext.getRaftState().getCurrentTerm()))
                    .build(), raftContext.getContext().self());
            return;
        }
        if (value.getStatus() == TxnStatus.FAILURE) {
            throw new IllegalStateException("failure state");
        }
        value.addListener(listener, retryNum);
    }

    public void createTxn(Key key, ActorRef<MediatorMessage> sender, int retryNum) {
        Value value = new Value(key.seqId, System.currentTimeMillis());
        value.addListener(sender, retryNum);
        value.setStatus(TxnStatus.WAITING);
        value.setInTxn(true);
        Value prev = table.put(key, value);
        assert prev == null || prev.getStatus() == TxnStatus.FAILURE;
    }

    public void markTxnFailure(Key key, Message msg) {
        Value value = table.get(key);
        if (value == null) {
            return;
        }
        assert value.isInTxn();
        assert msg instanceof MediatorMessage.FailureRes ||
               msg instanceof MediatorMessage.Redirect ||
               msg instanceof MediatorMessage.NoLeader;
        value.setInTxn(false);
        value.setStatus(TxnStatus.FAILURE);
        value.fireAndClearListener(msg);
    }

    public void close() {
        expireCheckSchedule.cancel(false);
    }

    public enum TxnStatus {
        WAITING,
        APPLIED,
        COMMITED,
        FAILURE,
    }

    public record Key(long clientId, long seqId) {
    }

    private record Listener(ActorRef<MediatorMessage> listener, int retryNum) {
    }

    private class Value {
        private final long seqId;
        private final long createTime;
        private Set<Listener> listeners;
        private TxnStatus status;
        private PeerProto.LogId appliedLogId;
        private List<Long> ackedSeqId;
        private boolean inTxn;

        public Value(long seqId, long createTime) {
            this.seqId = seqId;
            this.createTime = createTime;
        }

        public PeerProto.LogId getAppliedLogId() {
            return appliedLogId;
        }

        public void setAppliedLogId(PeerProto.LogId appliedLogId) {
            this.appliedLogId = appliedLogId;
        }

        public long getCreateTime() {
            return createTime;
        }

        public TxnStatus getStatus() {
            return status;
        }

        public void setStatus(TxnStatus status) {
            this.status = status;
        }

        public List<Long> getAckedSeqId() {
            return ackedSeqId;
        }

        public void setAckedSeqId(List<Long> ackedSeqId) {
            this.ackedSeqId = ackedSeqId;
        }

        public boolean isInTxn() {
            return inTxn;
        }

        public void setInTxn(boolean inTxn) {
            this.inTxn = inTxn;
        }

        public void addListener(ActorRef<MediatorMessage> listener, int retryNum) {
            Set<Listener> listeners = this.listeners;
            if (listeners == null) {
                this.listeners = Collections.singleton(new Listener(listener, retryNum));
            } else {
                this.listeners = new HashSet<>(listeners.size() + 1);
                this.listeners.addAll(listeners);
                this.listeners.add(new Listener(listener, retryNum));
            }
        }

        public boolean hasListener() {
            return listeners != null && !listeners.isEmpty();
        }

        public void fireAndClearListener(Message msg) {
            if (listeners == null || listeners.isEmpty()) {
                return;
            }
            ActorRef<PeerProto.PeerMessage> self = raftContext.getContext().self();
            MediatorMessage.Builder builder = MediatorMessage.newBuilder();
            switch (msg) {
                case MediatorMessage.TxnRes txnRes -> builder.setTxnRes(txnRes);
                case MediatorMessage.Redirect redirect -> builder.setRedirect(redirect);
                case MediatorMessage.NoLeader noLeader -> builder.setNoLeader(noLeader);
                case MediatorMessage.FailureRes failureRes -> builder.setFailureRes(failureRes);
                case MediatorMessage mediatorMessage -> builder.mergeFrom(mediatorMessage);
                default ->
                        throw new IllegalArgumentException("Unexpected value: " + msg.getClass());
            }
            Set<Listener> listeners = this.listeners;
            for (Listener listener : listeners) {
                listener.listener.tell((builder
                        .setSeqId(seqId)
                        .setRetryNum(listener.retryNum))
                        .build(), self);
            }
            this.listeners = null;
        }
    }
}
