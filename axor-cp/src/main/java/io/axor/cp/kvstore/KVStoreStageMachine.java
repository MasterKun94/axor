//package io.axor.cp.kvstore;
//
//import org.apache.ratis.conf.RaftProperties;
//import org.apache.ratis.proto.ExamplesProtos;
//import org.apache.ratis.proto.RaftProtos;
//import org.apache.ratis.protocol.Message;
//import org.apache.ratis.protocol.RaftClientRequest;
//import org.apache.ratis.protocol.RaftGroupId;
//import org.apache.ratis.server.RaftServer;
//import org.apache.ratis.server.storage.RaftStorage;
//import org.apache.ratis.statemachine.StateMachineStorage;
//import org.apache.ratis.statemachine.TransactionContext;
//import org.apache.ratis.statemachine.impl.BaseStateMachine;
//import org.apache.ratis.statemachine.impl.SimpleStateMachineStorage;
//import org.apache.ratis.thirdparty.com.google.protobuf.ByteString;
//import org.apache.ratis.thirdparty.com.google.protobuf.InvalidProtocolBufferException;
//import org.apache.ratis.util.FileUtils;
//import org.jetbrains.annotations.NotNull;
//
//import java.io.IOException;
//import java.nio.file.FileStore;
//import java.nio.file.Path;
//import java.util.concurrent.CompletableFuture;
//
//public class KVStoreStageMachine extends BaseStateMachine {
//    private final SimpleStateMachineStorage storage = new SimpleStateMachineStorage();
//
//    private final KVStore files;
//
//    public KVStoreStageMachine(KVStore files) {
//        this.files = files;
//    }
//
//    @Override
//    public void initialize(RaftServer server, RaftGroupId groupId, RaftStorage raftStorage)
//            throws IOException {
//        super.initialize(server, groupId, raftStorage);
//        this.storage.init(raftStorage);
//        FileUtils.createDirectories(files.getRootDir());
//    }
//
//    @Override
//    public StateMachineStorage getStateMachineStorage() {
//        return storage;
//    }
//
//    @Override
//    public void close() throws IOException {
//        files.close();
//        setLastAppliedTermIndex(null);
//    }
//
//    @Override
//    public CompletableFuture<Message> query(Message request) {
//        final ExamplesProtos.ReadRequestProto proto;
//        try {
//            proto = ExamplesProtos.ReadRequestProto.parseFrom(request.getContent());
//        } catch (InvalidProtocolBufferException e) {
//            return FileStoreCommon.completeExceptionally("Failed to parse " + request, e);
//        }
//
//        final String path = proto.getPath().toStringUtf8();
//        return (proto.getIsWatch()? files.watch(path)
//                : files.read(path, proto.getOffset(), proto.getLength(), true))
//                .thenApply(reply -> Message.valueOf(reply.toByteString()));
//    }
//
//    @Override
//    public TransactionContext startTransaction(RaftClientRequest request) throws IOException {
//        final ByteString content = request.getMessage().getContent();
//        final ExamplesProtos.FileStoreRequestProto proto = ExamplesProtos.FileStoreRequestProto
//        .parseFrom(content);
//        final TransactionContext.Builder b = TransactionContext.newBuilder()
//                .setStateMachine(this)
//                .setClientRequest(request);
//        if (proto.getRequestCase() == ExamplesProtos.FileStoreRequestProto.RequestCase.WRITE) {
//            final ExamplesProtos.WriteRequestProto write = proto.getWrite();
//            final ExamplesProtos.FileStoreRequestProto newProto = ExamplesProtos
//            .FileStoreRequestProto.newBuilder()
//                    .setWriteHeader(write.getHeader()).build();
//            b.setLogData(newProto.toByteString()).setStateMachineData(write.getData())
//                    .setStateMachineContext(newProto);
//        } else {
//            b.setLogData(content)
//                    .setStateMachineContext(proto);
//        }
//        return b.build();
//    }
//
//    @Override
//    public TransactionContext startTransaction(RaftProtos.LogEntryProto entry, RaftProtos
//    .RaftPeerRole role) {
//        return TransactionContext.newBuilder()
//                .setStateMachine(this)
//                .setLogEntry(entry)
//                .setServerRole(role)
//                .setStateMachineContext(getProto(entry))
//                .build();
//    }
//
//    @Override
//    public CompletableFuture<Integer> write(RaftProtos.LogEntryProto entry, TransactionContext
//    context) {
//        final ExamplesProtos.FileStoreRequestProto proto = getProto(context, entry);
//        if (proto.getRequestCase() != ExamplesProtos.FileStoreRequestProto.RequestCase
//        .WRITEHEADER) {
//            return null;
//        }
//
//        final ExamplesProtos.WriteRequestHeaderProto h = proto.getWriteHeader();
//        final CompletableFuture<Integer> f = files.write(entry.getIndex(),
//                h.getPath().toStringUtf8(), h.getClose(),  h.getSync(), h.getOffset(),
//                entry.getStateMachineLogEntry().getStateMachineEntry().getStateMachineData());
//        // sync only if closing the file
//        return h.getClose()? f: null;
//    }
//
//    static ExamplesProtos.FileStoreRequestProto getProto(TransactionContext context, RaftProtos
//    .LogEntryProto entry) {
//        if (context != null) {
//            final ExamplesProtos.FileStoreRequestProto proto = (ExamplesProtos
//            .FileStoreRequestProto) context.getStateMachineContext();
//            if (proto != null) {
//                return proto;
//            }
//        }
//        return getProto(entry);
//    }
//
//    static ExamplesProtos.FileStoreRequestProto getProto(RaftProtos.LogEntryProto entry) {
//        try {
//            return ExamplesProtos.FileStoreRequestProto.parseFrom(entry.getStateMachineLogEntry
//            ().getLogData());
//        } catch (InvalidProtocolBufferException e) {
//            throw new IllegalArgumentException("Failed to parse data, entry=" + entry, e);
//        }
//    }
//
//    @Override
//    public CompletableFuture<ByteString> read(RaftProtos.LogEntryProto entry,
//    TransactionContext context) {
//        final ExamplesProtos.FileStoreRequestProto proto = getProto(context, entry);
//        if (proto.getRequestCase() != ExamplesProtos.FileStoreRequestProto.RequestCase
//        .WRITEHEADER) {
//            return null;
//        }
//
//        final ExamplesProtos.WriteRequestHeaderProto h = proto.getWriteHeader();
//        CompletableFuture<ExamplesProtos.ReadReplyProto> reply =
//                files.read(h.getPath().toStringUtf8(), h.getOffset(), h.getLength(), false);
//
//        return reply.thenApply(ExamplesProtos.ReadReplyProto::getData);
//    }
//
//    static class LocalStream implements DataStream {
//        private final DataChannel dataChannel;
//
//        LocalStream(DataChannel dataChannel) {
//            this.dataChannel = dataChannel;
//        }
//
//        @Override
//        public DataChannel getDataChannel() {
//            return dataChannel;
//        }
//
//        @Override
//        public CompletableFuture<?> cleanUp() {
//            return CompletableFuture.supplyAsync(() -> {
//                try {
//                    dataChannel.close();
//                    return true;
//                } catch (IOException e) {
//                    return FileStoreCommon.completeExceptionally("Failed to close data
//                    channel", e);
//                }
//            });
//        }
//    }
//
//    @Override
//    public CompletableFuture<DataStream> stream(RaftClientRequest request) {
//        final ByteString reqByteString = request.getMessage().getContent();
//        final ExamplesProtos.FileStoreRequestProto proto;
//        try {
//            proto = ExamplesProtos.FileStoreRequestProto.parseFrom(reqByteString);
//        } catch (InvalidProtocolBufferException e) {
//            return FileStoreCommon.completeExceptionally(
//                    "Failed to parse stream header", e);
//        }
//        return files.createDataChannel(proto.getStream().getPath().toStringUtf8())
//                .thenApply(LocalStream::new);
//    }
//
//    @Override
//    public CompletableFuture<?> link(DataStream stream, RaftProtos.LogEntryProto entry) {
//        LOG.info("linking {}", stream);
//        return files.streamLink(stream);
//    }
//
//    @Override
//    public CompletableFuture<Message> applyTransaction(TransactionContext trx) {
//        final RaftProtos.LogEntryProto entry = trx.getLogEntry();
//
//        final long index = entry.getIndex();
//        updateLastAppliedTermIndex(entry.getTerm(), index);
//
//        final ExamplesProtos.FileStoreRequestProto request = getProto(trx, entry);
//
//        switch(request.getRequestCase()) {
//            case DELETE:
//                return delete(index, request.getDelete());
//            case WRITEHEADER:
//                return writeCommit(index, request.getWriteHeader(),
//                        entry.getStateMachineLogEntry().getStateMachineEntry()
//                        .getStateMachineData().size());
//            case STREAM:
//                return streamCommit(request.getStream());
//            case WRITE:
//                // WRITE should not happen here since
//                // startTransaction converts WRITE requests to WRITEHEADER requests.
//            default:
//                LOG.error(getId() + ": Unexpected request case " + request.getRequestCase());
//                return FileStoreCommon.completeExceptionally(index,
//                        "Unexpected request case " + request.getRequestCase());
//        }
//    }
//
//    private CompletableFuture<Message> writeCommit(
//            long index, ExamplesProtos.WriteRequestHeaderProto header, int size) {
//        final String path = header.getPath().toStringUtf8();
//        return files.submitCommit(index, path, header.getClose(), header.getOffset(), size)
//                .thenApply(reply -> Message.valueOf(reply.toByteString()));
//    }
//
//    private CompletableFuture<Message> streamCommit(ExamplesProtos.StreamWriteRequestProto
//    stream) {
//        final String path = stream.getPath().toStringUtf8();
//        final long size = stream.getLength();
//        return files.streamCommit(path, size).thenApply(reply -> Message.valueOf(reply
//        .toByteString()));
//    }
//
//    private CompletableFuture<Message> delete(long index, ExamplesProtos.DeleteRequestProto
//    request) {
//        final String path = request.getPath().toStringUtf8();
//        return files.delete(index, path).thenApply(resolved ->
//                Message.valueOf(ExamplesProtos.DeleteReplyProto.newBuilder().setResolvedPath(
//                                FileStoreCommon.toByteString(resolved)).build().toByteString(),
//                        () -> "Message:" + resolved));
//    }
//}
