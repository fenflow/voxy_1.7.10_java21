package me.cortex.voxy.legacy;

import java.util.LinkedHashMap;
import java.util.function.BiPredicate;

/** Client-thread queue. Retains the actual chunk until every requested section is accepted. */
public final class ChunkCaptureQueue<C> {
    private static final class Entry<C> {
        C chunk;
        int sections;
        Entry(C chunk) { this.chunk = chunk; }
    }
    private final LinkedHashMap<Long, Entry<C>> pending = new LinkedHashMap<>();
    private final java.util.LinkedHashSet<Long> departing = new java.util.LinkedHashSet<>();
    /** Finish departing chunks before background initial coverage. Retain FIFO order. */
    public void departing(long key, C chunk) {
        request(key, chunk, 0xffff);
        departing.add(key);
    }
    public void request(long key, C chunk, int sections) {
        var entry = pending.computeIfAbsent(key, ignored -> new Entry<>(chunk));
        entry.chunk = chunk;
        entry.sections |= sections & 0xffff;
    }
    public boolean captureOne(BiPredicate<C, Integer> capture) {
        if (pending.isEmpty()) return false;
        long key = departing.isEmpty() ? pending.keySet().iterator().next() : departing.iterator().next();
        var entry = pending.get(key);
        int section = Integer.numberOfTrailingZeros(entry.sections);
        if (!capture.test(entry.chunk, section)) return false;
        entry.sections &= ~(1 << section);
        if (entry.sections == 0) { pending.remove(key); departing.remove(key); }
        return true;
    }
    public int chunkCount() { return pending.size(); }
    public int sectionCount() { return pending.values().stream().mapToInt(e -> Integer.bitCount(e.sections)).sum(); }
    public boolean isEmpty() { return pending.isEmpty(); }
}
