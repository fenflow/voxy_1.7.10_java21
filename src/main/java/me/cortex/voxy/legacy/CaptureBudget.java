package me.cortex.voxy.legacy;

/** Catch up after packet bursts without permanently increasing stationary capture cost. */
public final class CaptureBudget {
    private CaptureBudget() {}
    public static int sectionLimit(int configured, int pending) {
        return Math.max(configured, Math.min(64, (pending + 7) / 8));
    }
    public static long nanoseconds(int pending) {
        return pending > 64 ? 8_000_000L : 3_000_000L;
    }
}
