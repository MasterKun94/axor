package io.axor.runtime.serde.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.Util;
import io.axor.runtime.AbstractSerdeFactory;
import io.axor.runtime.MsgType;
import io.axor.runtime.Registry;
import io.axor.runtime.Serde;
import io.axor.runtime.SerdeRegistry;

import java.lang.reflect.Modifier;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class KryoSerdeFactory extends AbstractSerdeFactory {
    private final ThreadLocal<KryoInstance> KRYO = ThreadLocal
            .withInitial(() -> new KryoInstance(new Kryo()));
    private final List<KryoInitializer> initializers = new CopyOnWriteArrayList<>();
    private final int bufferSize;
    private final int maxBufferSize;
    private volatile boolean initialized = false;

    public KryoSerdeFactory(SerdeRegistry registry) {
        this(8192, 0, registry);
    }

    public KryoSerdeFactory(int bufferSize, int maxBufferSize, SerdeRegistry registry) {
        super("kryo", registry);
        if (maxBufferSize == 0) {
            maxBufferSize = Integer.MAX_VALUE;
        }
        if (bufferSize > maxBufferSize) {
            throw new IllegalArgumentException("bufferSize " + bufferSize + " exceeds " +
                                               "maxBufferSize " + maxBufferSize);
        }
        this.bufferSize = bufferSize;
        this.maxBufferSize = maxBufferSize;
    }

    private void init() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    Registry.listAvailable(KryoInitializer.class)
                            .stream()
                            .sorted(Comparator.comparing(i -> i.getClass().getName()))
                            .forEach(initializers::add);
                    initialized = true;
                }
            }
        }
    }

    public void addInitializer(KryoInitializer initializer) {
        init();
        initializers.add(initializer);
    }

    KryoInstance getKryoInstance() {
        return KRYO.get();
    }

    @Override
    public boolean support(MsgType<?> type) {
        init();
        return true;
    }

    @Override
    public <T> Serde<T> create(MsgType<T> type) {
        init();
        if (isFinal(type.type())) {
            return new KryoSerde<>(type, this);
        }
        return new KryoAutoTypeSerde<>(type, this);
    }

    private boolean isFinal(Class<?> type) {
        if (type == null) {
            throw new IllegalArgumentException("type cannot be null.");
        } else {
            return type.isArray() ? Modifier.isFinal(Util.getElementClass(type).getModifiers()) :
                    Modifier.isFinal(type.getModifiers());
        }
    }

    @Override
    public String getImpl() {
        return "kryo";
    }

    public class KryoInstance {
        private final Kryo kryo;
        private final Output output;
        private final Input input;
        private int initOffset;

        private KryoInstance(Kryo kryo) {
            this.kryo = kryo;
            this.output = new Output(bufferSize, maxBufferSize);
            this.input = new Input(bufferSize);
        }

        public Kryo getKryo() {
            while (initOffset < initializers.size()) {
                initializers.get(initOffset++).initialize(kryo);
            }
            return kryo;
        }

        public Output getOutput() {
            return output;
        }

        public Input getInput() {
            return input;
        }
    }
}
