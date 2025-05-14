package io.axor.raft.file;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Test class for the TokenBucket class. Focuses on testing the calculateRequiredTime method with
 * various scenarios.
 */
public class TokenBucketTest {

    /**
     * Tests that no wait time is required when sufficient tokens are available.
     */
    @Test
    public void testCalculateRequiredTimeWithSufficientTokens() {
        TokenBucket bucket = new TokenBucket(10, 2);
        double requiredTime = bucket.calculateRequiredTime(5);
        assertEquals(0.0, requiredTime, 0.001);
    }

    /**
     * Tests the calculation of required time when tokens need to be refilled.
     */
    @Test
    public void testCalculateRequiredTimeWithTokenDeficit() {
        TokenBucket bucket = new TokenBucket(10, 2);
        bucket.tryAcquire(8); // Reduces tokens to 2
        double requiredTime = bucket.calculateRequiredTime(5);
        assertEquals(1.5, requiredTime, 0.001); // (5 - 2) / 2 = 1.5 seconds
    }

    /**
     * Tests that an exception is thrown when required tokens exceed bucket capacity.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testCalculateRequiredTimeWithExceedingCapacity() {
        TokenBucket bucket = new TokenBucket(10, 2);
        bucket.calculateRequiredTime(15); // Exceeds capacity of 10
    }

    /**
     * Tests the behavior when the bucket is empty and requires a full refill.
     */
    @Test
    public void testCalculateRequiredTimeWithEmptyBucket() {
        TokenBucket bucket = new TokenBucket(10, 2);
        bucket.tryAcquire(10); // Empties the bucket
        double requiredTime = bucket.calculateRequiredTime(5);
        assertEquals(2.5, requiredTime, 0.001); // (5 - 0) / 2 = 2.5 seconds
    }

    /**
     * Tests the edge case where required tokens equal the bucket capacity.
     */
    @Test
    public void testCalculateRequiredTimeWithExactCapacity() {
        TokenBucket bucket = new TokenBucket(10, 2);
        double requiredTime = bucket.calculateRequiredTime(10);
        assertEquals(0.0, requiredTime, 0.001); // Exact capacity, no wait time
    }
}
