package io.masterkun.axor.cluster.membership;

import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class VectorClockTest {

    @Test
    public void testCheck() {
        assertThrows(IllegalArgumentException.class, () -> {
            VectorClock.wrap(111, 0, 222);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            VectorClock.wrap(333, 2, 222, 1);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            VectorClock.wrap(333, 2, 222, 0);
        });
    }

    @Test
    public void testLoop() {
        VectorClock clock = VectorClock.wrap(111, 2, 222, 1);
        List<Entry> list1 = new ArrayList<>();
        List<Entry> list2 = new ArrayList<>();
        clock.foreach(((uid, c) -> list1.add(new Entry(uid, c))));
        clock.foreach((uid, c) -> {
            list2.add(new Entry(uid, c));
        });
        assertEquals(list1, list2);
        assertEquals(2, list1.size());
        assertEquals(new Entry(111, 2), list2.get(0));
        assertEquals(new Entry(222, 1), list2.get(1));
    }

    @Test
    public void testMerge() {
        VectorClock clock1 = VectorClock.wrap(111, 1, 222, 2);
        VectorClock clock2 = VectorClock.wrap(222, 1, 333, 1);
        VectorClock clock3 = VectorClock.wrap(11, 2, 444, 1);
        VectorClock clock4 = VectorClock.wrap(111, 4, 333, 2, 444, 4);
        assertEquals(VectorClock.wrap(111, 1, 222, 2, 333, 1), clock1.merge(clock2));
        assertEquals(VectorClock.wrap(11, 2, 222, 1, 333, 1, 444, 1), clock2.merge(clock3));
        VectorClock merged = clock1.merge(clock2).merge(clock3).merge(clock4);
        assertEquals(VectorClock.wrap(11, 2, 111, 4, 222, 2, 333, 2, 444, 4), merged);
        assertEquals(VectorClock.wrap(11, 2, 111, 4, 222, 2, 333, 2, 444, 5), merged.inc(444));

        Queue<MergeEntry> entries = new LinkedList<>();
        clock3.merge(clock4, (uid, leftClock, rightClock) -> {
            entries.add(new MergeEntry(uid, leftClock, rightClock));
        });
        assertEquals(4, entries.size());
        assertEquals(new MergeEntry(11, 2, 0), entries.poll());
        assertEquals(new MergeEntry(111, 0, 4), entries.poll());
        assertEquals(new MergeEntry(333, 0, 2), entries.poll());
        assertEquals(new MergeEntry(444, 1, 4), entries.poll());
    }

    @Test
    public void testBinarySearch() {
        VectorClock clock0 = VectorClock.wrap(111, 1);
        VectorClock clock1 = VectorClock.wrap(111, 1, 222, 2);
        VectorClock clock2 = VectorClock.wrap(222, 1, 333, 2, 444, 3);
        VectorClock clock3 = VectorClock.wrap(222, 1, 333, 2, 444, 3, 555, 4);
        assertEquals(0, clock0.get(222));
        assertEquals(0, clock1.get(11));
        assertEquals(0, clock1.get(333));
        assertEquals(0, clock2.get(11));
        assertEquals(0, clock2.get(555));
        assertEquals(0, clock3.get(11));
        assertEquals(0, clock3.get(666));

        assertEquals(1, clock0.get(111));
        assertEquals(1, clock1.get(111));
        assertEquals(2, clock1.get(222));
        assertEquals(1, clock2.get(222));
        assertEquals(2, clock2.get(333));
        assertEquals(3, clock2.get(444));
        assertEquals(1, clock3.get(222));
        assertEquals(2, clock3.get(333));
        assertEquals(3, clock3.get(444));
        assertEquals(4, clock3.get(555));
    }

    @Test
    public void testCompare() {
        VectorClock clock1 = VectorClock.wrap(111, 1, 222, 2);
        VectorClock clock2 = VectorClock.wrap(222, 1, 333, 1);
        VectorClock clock3 = VectorClock.wrap(111, 2, 444, 1);
        VectorClock clock4 = VectorClock.wrap(111, 2, 444, 1, 555, 1);

        assertFalse(clock1.isEarlierThan(clock2));
        assertFalse(clock3.isEarlierThan(clock1));
        assertFalse(clock3.isEarlierThan(clock2));
        assertFalse(clock1.isEarlierThan(clock2, 111));
        assertFalse(clock3.isEarlierThan(clock1, 111));
        assertFalse(clock3.isEarlierThan(clock2, 111));
        assertFalse(clock1.isEarlierThan(clock2, 222));
        assertFalse(clock2.isEarlierThan(clock3, 222));
        assertFalse(clock2.isEarlierThan(clock3, 333));

        assertTrue(clock1.isLaterThan(clock2));
        assertTrue(clock3.isLaterThan(clock1));
        assertTrue(clock3.isLaterThan(clock2));
        assertTrue(clock1.isLaterThan(clock2, 111));
        assertTrue(clock3.isLaterThan(clock1, 111));
        assertTrue(clock3.isLaterThan(clock2, 111));
        assertTrue(clock1.isLaterThan(clock2, 222));
        assertTrue(clock2.isLaterThan(clock3, 222));
        assertTrue(clock2.isLaterThan(clock3, 333));

        assertFalse(clock3.isEarlierThan(clock1, 333));
        assertFalse(clock1.isEarlierThan(clock3, 333));

        assertTrue(clock4.isLaterThan(clock3));
        assertFalse(clock4.isEarlierThan(clock3));
    }

    @Test
    public void filter() {
        VectorClock clock1 = VectorClock.wrap(111, 1, 222, 2, 333, 3, 444, 4);
        assertSame(clock1, clock1.filter(l -> true));
        assertEquals(VectorClock.wrap(111, 1, 222, 2, 333, 3), clock1.remove(444));
        assertEquals(VectorClock.wrap(111, 1), clock1.filter(l -> l == 111));
        assertEquals(VectorClock.wrap(222, 2), clock1.filter(l -> l == 222));
        assertEquals(VectorClock.wrap(222, 2, 444, 4), clock1.filter(l -> l == 222 || l == 444));
        assertEquals(VectorClock.wrap(111, 1, 333, 3), clock1.filter(l -> l == 111 || l == 333));
    }

    public record Entry(long uid, long clock) {
    }

    private record MergeEntry(long uid, long leftClock, long rightClock) {
    }
}
