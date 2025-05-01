package io.axor.cp.raft;

import io.axor.api.AbstractActor;
import io.axor.api.ActorContext;
import io.axor.api.ActorRef;
import io.axor.api.Behavior;
import io.axor.api.Behaviors;
import io.axor.api.InternalSignals;
import io.axor.api.impl.ActorUnsafe;
import io.axor.commons.concurrent.EventPromise;
import io.axor.commons.concurrent.EventStage;
import io.axor.cp.messages.AppendStatus;
import io.axor.cp.messages.CommitStatus;
import io.axor.cp.messages.LeaderState;
import io.axor.cp.messages.LogAppend;
import io.axor.cp.messages.LogAppendAck;
import io.axor.cp.messages.LogCommit;
import io.axor.cp.messages.LogCommitAck;
import io.axor.cp.messages.LogEntryId;
import io.axor.cp.messages.LogFetch;
import io.axor.cp.messages.LogFetchRes;
import io.axor.cp.messages.NodeMessage;
import io.axor.cp.messages.RaftMessage;
import io.axor.cp.messages.ResetUncommitedSignal;
import io.axor.cp.raft.logging.AsyncRaftLogging;
import io.axor.cp.raft.logging.AsyncRaftLoggingFactory;
import io.axor.runtime.MsgType;
import io.axor.runtime.Signal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Replicator extends AbstractActor<RaftMessage> {
    private static final Logger LOG = LoggerFactory.getLogger(Replicator.class);

    private final String name;
    private final AsyncRaftLoggingFactory raftLoggingFactory;
    private final ActorRef<RaftMessage> nodeRef;
    private final RaftContext raftContext;
    private AsyncRaftLogging raftLogging;

    protected Replicator(ActorContext<RaftMessage> context,
                         String name,
                         RaftContext raftContext,
                         AsyncRaftLoggingFactory raftLoggingFactory,
                         ActorRef<RaftMessage> nodeRef) {
        super(context);
        this.name = name;
        this.raftContext = raftContext;
        this.raftLoggingFactory = raftLoggingFactory;
        this.nodeRef = nodeRef;
    }

    @Override
    protected Behavior<RaftMessage> initialBehavior() {
        return initializing();
    }

    private Behavior<RaftMessage> initializing() {
        raftLoggingFactory.create(name, context().dispatcher().newPromise())
                .observe(((asyncRaftLogging, throwable) -> {
                    if (throwable != null) {
                        LOG.error("Replicator[{}] load failure", name, throwable);
                        ActorUnsafe.signalInline(self(), new ReplicatorLoadFailure());
                    } else {
                        LOG.error("Replicator[{}] load success", name);
                        Replicator.this.raftLogging = asyncRaftLogging;
                        ActorUnsafe.signalInline(self(), new ReplicatorLoadSuccess());
                    }
                }));
        return Behaviors.receive(msg -> Behaviors.unhandled(), signal -> {
            if (signal instanceof ReplicatorLoadSuccess()) {
                ActorUnsafe.signal(nodeRef, signal);
                raftContext.updateCommited(raftLogging.commitedId());
                raftContext.updateLeaderLastHeartbeat();
                return startup();
            } else if (signal instanceof ReplicatorLoadFailure()) {
                ActorUnsafe.signal(nodeRef, signal);
                return Behaviors.stop();
            }
            return Behaviors.unhandled();
        });
    }

    private Behavior<RaftMessage> startup() {
        return Behaviors.receive(msg -> {
            if (msg instanceof LogAppend(var txnId, var entries, var ignore)) {
                ActorRef<RaftMessage> leader = sender(RaftMessage.class);
                assert raftContext.isLeader(leader);
                raftLogging.append(entries, context().dispatcher().newPromise())
                        .observe((status, e) -> {
                            if (e != null) {
                                LOG.error("[{}] append error", msg, e);
                                status = AppendStatus.SYSTEM_ERROR;
                            } else {
                                LOG.debug("[{}] append with status {}", msg, status);
                            }
                            leader.tell(new LogAppendAck(txnId, status, raftContext.commitedId()));
                        });
                return Behaviors.same();
            } else if (msg instanceof LogCommit cmt) {
                ActorRef<RaftMessage> leader = sender(RaftMessage.class);
                assert leader.equals(nodeRef);
                assert raftContext.isLeader(leader);
                assert raftContext.isLeader(raftContext.selfPeer());
                logCommit(cmt, cmt.commitId()).observe((status, e) -> {
                    if (e != null) {
                        status = CommitStatus.SYSTEM_ERROR;
                    }
                    leader.tell(new LogCommitAck(cmt.txnId(), status, raftContext.commitedId()));
                });
                return Behaviors.same();
            } else if (msg instanceof LeaderState(LogEntryId currentCommited)) {
                ActorRef<RaftMessage> leader = sender(RaftMessage.class);
                assert raftContext.isLeader(leader);
                logCommit((LeaderState) msg, currentCommited);
                return Behaviors.same();
            } else if (msg instanceof LogFetch(var txnId, var id, var entryLimit, var sizeLimit)) {
                ActorRef<RaftMessage> sender = sender(RaftMessage.class);
                raftLogging.read(id, entryLimit, sizeLimit, context().dispatcher().newPromise())
                        .observe((l, e) -> {
                            if (e != null) {
                                sender.tell(LogFetchRes.failure(txnId, e));
                            } else {
                                sender.tell(LogFetchRes.success(txnId, l));
                            }
                        });
                return Behaviors.same();
            }

            return Behaviors.unhandled();
        }, signal -> {
            if (signal instanceof ResetUncommitedSignal) {
                raftLogging.resetUncommited(EventPromise.noop(context().dispatcher()));
                return Behaviors.same();
            }
            return Behaviors.unhandled();
        });
    }

    private EventStage<CommitStatus> logCommit(NodeMessage msg, LogEntryId id) {
        return raftLogging.commit(id, context().dispatcher().newPromise())
                .observe((status, e) -> {
                    if (e != null) {
                        LOG.error("[{}] commit error", msg, e);
                    } else {
                        LOG.debug("[{}] commit with status {}", msg, status);
                        assert id.equals(msg.currentCommited());
                        raftContext.updateCommited(id);
                    }
                });
    }

    @Override
    public MsgType<RaftMessage> msgType() {
        return MsgType.of(RaftMessage.class);
    }

    public record ReplicatorLoadSuccess() implements Signal {}
    public record ReplicatorLoadFailure() implements Signal {}
}
