package io.axor.cluster.membership;

import io.axor.api.ActorAddress;

import java.net.InetSocketAddress;
import java.util.Objects;

public class MemberIdGenerator {
    private static final long EPOCH = 1577808000000L; // 2020-01-01 00:00:00
    private static final long WORKER_ID_BITS = 14L;
    private static final long DATACENTER_ID_BITS = 8L;
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);
    private static final long WORKER_ID_SHIFT = 0;
    private static final long DATACENTER_ID_SHIFT = WORKER_ID_BITS;
    private static final long TIMESTAMP_LEFT_SHIFT = WORKER_ID_BITS + DATACENTER_ID_BITS;

    private final long workerId;
    private final long datacenterId;
    private long lastTimestamp = -1L;

    public MemberIdGenerator(long workerId, long datacenterId) {
        this.workerId = Math.abs(workerId) % (MAX_WORKER_ID + 1);
        this.datacenterId = Math.abs(datacenterId) % (MAX_DATACENTER_ID + 1);
    }

    public static MemberIdGenerator create(ActorAddress path) {
        var address = new InetSocketAddress(path.host(), path.port());
        byte[] addr = address.getAddress().getAddress();
        long addrLong = bytesToLong(addr);
        long workerId = Objects.hash(path.system(), path.name()) + path.port();
        return new MemberIdGenerator(addrLong, workerId);
    }

    private static long bytesToLong(byte[] bytes) {
        long result = 0;
        int len = bytes.length;
        for (int i = 0; i < len; i++) {
            result = result | ((long) (bytes[i] & 0xFF) << ((len - i - 1) << 3));
        }
        return result;
    }

    public static void main(String[] args) {
        MemberIdGenerator idGenerator = new MemberIdGenerator(0, 0);
        for (int i = 0; i < 10; i++) {
            System.out.println(idGenerator.nextId());
        }
        byte[] bytes = new byte[]{1, 2, 3, 4};
        byte[] bytes2 = new byte[]{1, 2, 3, 5};
        byte[] bytes3 = new byte[]{1, 2, 3, 4, 5, 6};
        byte[] bytes4 = new byte[]{1, 2, 3, 4, 5, 7};
        System.out.println(bytesToLong(bytes));
        System.out.println(bytesToLong(bytes2));
        System.out.println(bytesToLong(bytes3));
        System.out.println(bytesToLong(bytes4));
    }

    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();
        if (timestamp < lastTimestamp) {
            throw new RuntimeException("Clock moved backwards. Refusing to generate ID.");
        }
        if (timestamp == lastTimestamp) {
            timestamp = tilNextMillis(lastTimestamp);
        }
        lastTimestamp = timestamp;
        return ((timestamp - EPOCH) << TIMESTAMP_LEFT_SHIFT) |
               (datacenterId << DATACENTER_ID_SHIFT) |
               (workerId << WORKER_ID_SHIFT);
    }

    private long tilNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            Thread.yield();
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }
}
