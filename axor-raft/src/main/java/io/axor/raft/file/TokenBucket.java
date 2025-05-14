package io.axor.raft.file;

import java.util.concurrent.TimeUnit;

/**
 * 令牌桶算法工具类（线程安全） 功能：流量控制、计算获取令牌的预期等待时间
 */
public class TokenBucket {
    private final long capacity;      // 桶的最大容量
    private final long refillRate;    // 令牌填充速率（令牌/秒）
    private long tokens;              // 当前令牌数量
    private long lastRefillNanos;       // 最后一次填充时间（纳秒）

    /**
     * 构造函数
     *
     * @param capacity   桶容量（最大令牌数）
     * @param refillRate 令牌填充速率（令牌/秒）
     */
    public TokenBucket(long capacity, long refillRate) {
        if (capacity <= 0 || refillRate <= 0) {
            throw new IllegalArgumentException("Capacity and rate must be positive");
        }
        this.capacity = capacity;
        this.refillRate = refillRate;
        this.tokens = capacity;
        this.lastRefillNanos = System.nanoTime();
    }

    // 测试用例
    public static void main(String[] args) throws Exception {
        TokenBucket bucket = new TokenBucket(10, 2); // 容量10，速率2个/秒

        // 测试计算等待时间
        System.out.println("首次获取5个需要等待: " + bucket.calculateRequiredTime(5) + "秒");

        // 获取3个令牌
        bucket.tryAcquire(3);
        System.out.println("剩余令牌: " + bucket.tokens);

        // 计算获取5个需要等待的时间
        System.out.println("需要等待: " + bucket.calculateRequiredTime(5) + "秒");

        // 获取3个令牌
        bucket.tryAcquire(7);
        System.out.println("剩余令牌: " + bucket.tokens);
        // 等待2秒后再次计算
        TimeUnit.SECONDS.sleep(2);
        System.out.println("等待2秒后需要: " + bucket.calculateRequiredTime(5) + "秒");
    }

    /**
     * 计算获取指定数量令牌需要等待的时间（秒）
     *
     * @param requiredTokens 需要的令牌数量
     * @return 需要等待的时间（秒），如果无需等待返回0
     */
    public synchronized double calculateRequiredTime(long requiredTokens) {
        if (requiredTokens > capacity) {
            throw new IllegalArgumentException(
                    "Required tokens cannot exceed bucket capacity");
        }

        refillTokens(); // 先补充令牌

        if (tokens >= requiredTokens) {
            return 0.0;
        }

        double deficit = requiredTokens - tokens;
        return deficit / refillRate;
    }

    /**
     * 尝试立即获取令牌（非阻塞）
     *
     * @param requestedTokens 请求的令牌数量
     * @return 是否获取成功
     */
    public synchronized boolean tryAcquire(long requestedTokens) {
        refillTokens();
        if (tokens >= requestedTokens) {
            tokens -= requestedTokens;
            return true;
        }
        return false;
    }

    /**
     * 阻塞获取令牌
     *
     * @param requestedTokens 请求的令牌数量
     */
    public synchronized void acquire(long requestedTokens)
            throws InterruptedException {

        while (true) {
            double waitTime = calculateRequiredTime(requestedTokens);
            if (waitTime <= 0) {
                tokens -= requestedTokens;
                return;
            }

            TimeUnit.NANOSECONDS.sleep((long) (waitTime * 1e9));
            refillTokens();
        }
    }

    // 补充令牌逻辑
    private void refillTokens() {
        long now = System.nanoTime();
        double elapsedTime = (now - lastRefillNanos) / 1e9;
        long newTokens = (long) (elapsedTime * refillRate);

        tokens = Math.min(tokens + newTokens, capacity);
        lastRefillNanos = now;
    }
}
