package io.axor.runtime.impl;

import io.axor.runtime.MsgType;
import io.axor.runtime.Serde;
import io.axor.runtime.SerdeRegistry;
import io.axor.runtime.StreamAddress;
import io.axor.runtime.StreamDefinition;
import io.axor.runtime.Unsafe;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressWarnings({"rawtypes", "unchecked"})
public class DefaultBuiltinSerdeFactoryInitializer implements BuiltinSerdeFactoryInitializer {

    private static DataInputStream getDataInputStream(InputStream inputStream) {
        return inputStream instanceof DataInputStream ?
                (DataInputStream) inputStream :
                new DataInputStream(inputStream);
    }

    public static DataOutputStream getDataOutputStream(OutputStream outputStream) {
        return outputStream instanceof DataOutputStream ?
                (DataOutputStream) outputStream :
                new DataOutputStream(outputStream);
    }

    @Override
    public void initialize(BuiltinSerdeFactory factory, SerdeRegistry registry) {
        factory.register(MsgType.class, new MsgTypeSerde(registry));
        factory.register(Serde.class, new SerdeSerde(registry));
        factory.register(StreamAddress.class, new StreamAddressSerde());
        factory.register(StreamDefinition.class, new StreamDefinitionSerde(registry));
    }

    private static final class MsgTypeSerde implements BuiltinSerde<MsgType> {
        private final SerdeRegistry registry;

        private MsgTypeSerde(SerdeRegistry registry) {
            this.registry = registry;
        }

        @Override
        public void doSerialize(MsgType object, DataOutput out) throws IOException {
            int id = registry.findIdByType(object);
            if (id > 0) {
                out.writeBoolean(true);
                out.writeInt(id);
            } else {
                out.writeBoolean(false);
                out.writeUTF(object.type().getName());
                List<MsgType> args = object.typeArgs();
                int size = args.size();
                out.writeShort(size);
                if (size > 0) {
                    for (MsgType arg : args) {
                        doSerialize(arg, out);
                    }
                }
            }
        }

        @Override
        public MsgType doDeserialize(DataInput in) throws IOException {
            if (in.readBoolean()) {
                return registry.getTypeById(in.readInt());
            }
            Class<?> type;
            int size;
            try {
                type = Class.forName(in.readUTF());
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
            size = in.readShort();
            if (size == 0) {
                return MsgType.of(type);
            }
            List<MsgType<?>> typeArguments = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                typeArguments.add(this.doDeserialize(in));
            }
            return Unsafe.msgType(type, Collections.unmodifiableList(typeArguments));
        }

        @Override
        public MsgType<MsgType> getType() {
            return MsgType.of(MsgType.class);
        }
    }

    private static class SerdeSerde implements BuiltinSerde<Serde> {
        private final MsgTypeSerde msgTypeSerde;
        private final SerdeRegistry serdeRegistry;

        private SerdeSerde(SerdeRegistry serdeRegistry) {
            this.serdeRegistry = serdeRegistry;
            this.msgTypeSerde = new MsgTypeSerde(serdeRegistry);
        }

        @Override
        public void doSerialize(Serde object, DataOutput out) throws IOException {
            out.writeUTF(object.getImpl());
            if (object.getImpl().equals(NoopSerdeFactory.NAME)) {
                return;
            }
            msgTypeSerde.doSerialize(object.getType(), out);
        }

        @Override
        public Serde doDeserialize(DataInput in) throws IOException {
            String impl;
            try {
                impl = in.readUTF();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if (impl.equals(NoopSerdeFactory.NAME)) {
                return new NoopSerdeFactory.NoopSerde(MsgType.of(Object.class));
            }
            MsgType msgType = msgTypeSerde.doDeserialize(in);
            return serdeRegistry.create(impl, msgType);
        }

        @Override
        public MsgType<Serde> getType() {
            return MsgType.of(Serde.class);
        }
    }

    private static class StreamAddressSerde implements BuiltinSerde<StreamAddress> {

        @Override
        public void doSerialize(StreamAddress obj, DataOutput out) throws IOException {
            out.writeUTF(obj.host());
            out.writeInt(obj.port());
            out.writeUTF(obj.service());
            out.writeUTF(obj.method());
        }

        @Override
        public StreamAddress doDeserialize(DataInput in) throws IOException {
            return new StreamAddress(in.readUTF(), in.readInt(), in.readUTF(), in.readUTF());
        }

        @Override
        public MsgType<StreamAddress> getType() {
            return MsgType.of(StreamAddress.class);
        }
    }

    private static class StreamDefinitionSerde implements BuiltinSerde<StreamDefinition> {
        private final SerdeSerde serdeSerde;
        private final StreamAddressSerde streamAddressSerde = new StreamAddressSerde();

        private StreamDefinitionSerde(SerdeRegistry serdeRegistry) {
            this.serdeSerde = new SerdeSerde(serdeRegistry);
        }

        @Override
        public void doSerialize(StreamDefinition obj, DataOutput out) throws IOException {
            streamAddressSerde.doSerialize(obj.address(), out);
            serdeSerde.doSerialize(obj.serde(), out);
        }

        @Override
        public StreamDefinition doDeserialize(DataInput in) throws IOException {
            return new StreamDefinition(
                    streamAddressSerde.doDeserialize(in),
                    serdeSerde.doDeserialize(in)
            );
        }

        @Override
        public MsgType<StreamDefinition> getType() {
            return MsgType.of(StreamDefinition.class);
        }
    }
}
