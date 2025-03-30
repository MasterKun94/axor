package io.masterkun.axor.runtime.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class PlatformDependent {
    private static final Logger LOG = LoggerFactory.getLogger(PlatformDependent.class);
    private static final List<String> QUEUE_TYPES = List.of(
            "org.jctools.queues.MpscUnboundedArrayQueue",
            "io.netty.util.internal.shaded.org.jctools.queues.MpscUnboundedArrayQueue",
            "io.grpc.netty.shaded.io.netty.util.internal.shaded.org.jctools.queues" +
            ".MpscUnboundedArrayQueue",
            "org.jctools.queues.atomic.MpscUnboundedAtomicArrayQueue",
            "io.netty.util.internal.shaded.org.jctools.queues.atomic.MpscUnboundedAtomicArrayQueue",
            "io.grpc.netty.shaded.io.netty.util.internal.shaded.org.jctools.queues.atomic" +
            ".MpscUnboundedAtomicArrayQueue"
    );
    private static volatile Class<?> enabledQueueType;

    public static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    private static void init() {
        if (enabledQueueType == null) {
            for (String queueType : QUEUE_TYPES) {
                try {
                    enabledQueueType = Class.forName(queueType);
                    break;
                } catch (ClassNotFoundException e) {
                    // do nothing
                }
            }
            if (enabledQueueType == null) {
                enabledQueueType = ConcurrentLinkedQueue.class;
            }
            LOG.debug("Enabled queue type: {}", enabledQueueType);
        }
    }

    public static <T> Queue<T> newMpscQueue() {
        return newMpscQueue(1024);
    }

    public static <T> Queue<T> newMpscQueue(int initialCapacity) {
        init();
        if (enabledQueueType == ConcurrentLinkedQueue.class) {
            return new ConcurrentLinkedQueue<>();
        }
        try {
            //noinspection unchecked
            return (Queue<T>) enabledQueueType.getConstructor(int.class).newInstance(initialCapacity);
        } catch (InstantiationException | NoSuchMethodException |
                 InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
