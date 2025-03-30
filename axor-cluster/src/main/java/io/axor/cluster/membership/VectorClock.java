package io.axor.cluster.membership;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.LongPredicate;

import static java.lang.Math.max;

public final class VectorClock implements Comparable<VectorClock> {
    private final long[] vector;

    VectorClock(long[] vector) {
        assert vector.length > 0;
        this.vector = vector;
    }

    static VectorClock wrap(long... vector) {
        long before = -1;
        int len = vector.length;
        if (len == 0) throw new IllegalArgumentException("vector is empty");
        if (len % 2 != 0) throw new IllegalArgumentException("vector must be even");
        for (int i = 0; i < len; i += 2) {
            long l = vector[i];
            if (before >= l) throw new IllegalArgumentException("vector not sorted");
            if (vector[i + 1] <= 0) throw new IllegalArgumentException("clock must be positive");
            before = l;
        }
        return new VectorClock(vector);
    }

    long[] array() {
        return vector;
    }

    VectorClock unsafeInc(long uid) {
        int i = binarySearchIdx(uid);
        if (i == -1) {
            return merge(VectorClock.wrap(uid, 1), vector.length + 1);
        }
        vector[i + 1]++;
        return this;
    }

    public VectorClock inc(long uid) {
        int i = binarySearchIdx(uid);
        if (i == -1) {
            return merge(VectorClock.wrap(uid, 1), vector.length + 1);
        }
        var vector = this.vector.clone();
        vector[i + 1]++;
        return new VectorClock(vector);
    }

    public VectorClock remove(long uid) {
        int i = binarySearchIdx(uid);
        if (i == -1) {
            return this;
        }
        long[] newVector = new long[vector.length - 2];
        System.arraycopy(vector, 0, newVector, 0, i);
        System.arraycopy(vector, i + 2, newVector, i, vector.length - i - 2);
        return new VectorClock(newVector);
    }

    public VectorClock filter(LongPredicate filter) {
        long[] output = null;
        int outputOff = -1;
        long[] vector = this.vector;
        for (int i = 0, l = vector.length; i < l; i += 2) {
            long uid = vector[i];
            if (filter.test(uid)) {
                if (output != null) {
                    output[outputOff++] = uid;
                    output[outputOff++] = vector[i + 1];
                }
            } else if (output == null) {
                output = new long[vector.length];
                System.arraycopy(vector, 0, output, 0, outputOff = i);
            }
        }
        return output == null ?
                this :
                new VectorClock(Arrays.copyOf(output, outputOff));
    }

    public VectorClock merge(VectorClock vectorClock) {
        return merge(vectorClock, max(vector.length, vectorClock.vector.length));
    }

    public VectorClock merge(VectorClock vectorClock, int combinedLen) {
        return merge(vectorClock, combinedLen, (uid, l, r) -> {
        });
    }

    public VectorClock merge(VectorClock vectorClock, MergeHandler handler) {
        return merge(vectorClock, max(vector.length, vectorClock.vector.length), handler);
    }

    private VectorClock merge(VectorClock vectorClock, int combinedLen, MergeHandler handler) {
        long[] leftVector = vector, rightVector = vectorClock.array();
        int rightLen = rightVector.length, leftLen = leftVector.length;
        long[] combineVector = new long[combinedLen];
        int leftOff = 0, rightOff = 0, combineOff = 0;
        boolean leftHasNext = true, rightHasNext = true;
        do {
            if (combineOff == combinedLen) {
                int newLen = vector.length + vectorClock.vector.length;
                long[] newVector = new long[newLen];
                System.arraycopy(combineVector, 0, newVector, 0, combineOff);
                combineVector = newVector;
                combinedLen = newLen;
            }
            if (!leftHasNext) {
                long uid = rightVector[rightOff++];
                long clock = rightVector[rightOff++];
                handler.handle(uid, 0, clock);
                combineVector[combineOff++] = uid;
                combineVector[combineOff++] = clock;
                rightHasNext = rightOff < rightLen;
            } else if (!rightHasNext) {
                long uid = leftVector[leftOff++];
                long clock = leftVector[leftOff++];
                handler.handle(uid, clock, 0);
                combineVector[combineOff++] = uid;
                combineVector[combineOff++] = clock;
                leftHasNext = leftOff < leftLen;
            } else {
                long leftUid = leftVector[leftOff], rightUid = rightVector[rightOff];
                if (leftUid < rightUid) {
                    long clock = leftVector[leftOff + 1];
                    handler.handle(leftUid, clock, 0);
                    combineVector[combineOff++] = leftUid;
                    combineVector[combineOff++] = clock;
                    leftHasNext = (leftOff += 2) < leftLen;
                } else if (rightUid < leftUid) {
                    long clock = rightVector[rightOff + 1];
                    handler.handle(rightUid, 0, clock);
                    combineVector[combineOff++] = rightUid;
                    combineVector[combineOff++] = clock;
                    rightHasNext = (rightOff += 2) < rightLen;
                } else {
                    long leftClock = leftVector[leftOff + 1], rightClock =
                            rightVector[rightOff + 1];
                    handler.handle(leftUid, leftClock, rightClock);
                    combineVector[combineOff++] = rightUid;
                    combineVector[combineOff++] = max(leftClock, rightClock);
                    leftHasNext = (leftOff += 2) < leftLen;
                    rightHasNext = (rightOff += 2) < rightLen;
                }
            }
        } while (leftHasNext || rightHasNext);
        if (combineOff == combinedLen) {
            return wrap(combineVector);
        } else {
            return wrap(Arrays.copyOf(combineVector, combineOff));
        }
    }

    private int binarySearchIdx(long uid) {
        long[] vector = this.vector;
        int offRight = vector.length >>> 1;
        int offLeft = 0;
        while (offLeft < offRight) {
            int off = (offLeft + offRight) >>> 1;
            int idx = off << 1;
            long l = vector[idx];
            if (l == uid) {
                return idx;
            } else if (l > uid) {
                offRight = off;
            } else {
                offLeft = off + 1;
            }
        }
        return -1;
    }

    public long get(long uid) {
        int idx = binarySearchIdx(uid);
        return idx == -1 ? 0 : vector[idx + 1];
    }

    public boolean isEarlierThan(VectorClock vectorClock) {
        return this.compareTo(vectorClock) < 0;
    }

    public boolean isEarlierThan(VectorClock vectorClock, long uid) {
        return get(uid) < vectorClock.get(uid);
    }

    public boolean isLaterThan(VectorClock vectorClock) {
        return this.compareTo(vectorClock) > 0;
    }

    public boolean isLaterThan(VectorClock vectorClock, long uid) {
        return get(uid) > vectorClock.get(uid);
    }

    public int size() {
        return vector.length >> 1;
    }

    public void foreach(Acceptor acceptor) {
        long[] vector = this.vector;
        long length = vector.length;
        for (int i = 0; i < length; i += 2) {
            acceptor.accept(vector[i], vector[i + 1]);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        VectorClock entries = (VectorClock) o;
        return Objects.deepEquals(vector, entries.vector);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(vector);
    }

    @Override
    public int compareTo(VectorClock o) {
        int i = 0;
        int len = Math.min(vector.length, o.vector.length);
        while (i < len) {
            long leftId = vector[i];
            long rightId = o.vector[i];
            if (leftId != rightId) {
                return -Long.compare(leftId, rightId);
            }
            long leftClock = vector[i + 1];
            long rightClock = o.vector[i + 1];
            if (leftClock != rightClock) {
                return Long.compare(leftClock, rightClock);
            }
            i += 2;
        }
        return Integer.compare(vector.length, o.vector.length);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[");
        foreach(new Acceptor() {
            boolean first = true;

            @Override
            public void accept(long uid, long clock) {
                if (first) first = false;
                else sb.append(", ");
                sb.append(uid).append(':').append(clock);
            }
        });
        return sb.append("]").toString();
    }

    public interface Acceptor {
        void accept(long uid, long clock);
    }

    public interface MergeHandler {
        void handle(long uid, long leftClock, long rightClock);
    }
}
