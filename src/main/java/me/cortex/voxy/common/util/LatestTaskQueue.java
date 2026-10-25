package me.cortex.voxy.common.util;

import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;

/** Coalesces queued work and prevents late, obsolete builds from replacing newer results. */
public final class LatestTaskQueue<T> implements AutoCloseable {
    public record Task<T>(long key, T value) { }
    private final LinkedHashMap<Long, Task<T>> waiting = new LinkedHashMap<>();
    private final ConcurrentHashMap<Long, Task<T>> current = new ConcurrentHashMap<>();
    private final Object delivery = new Object();
    private boolean closed;
    public synchronized void enqueue(long key, T value) {
        if (closed) return;
        var task = new Task<>(key, value);
        current.put(key, task);
        waiting.put(key, task);
        notifyAll();
    }
    public synchronized Task<T> take() throws InterruptedException {
        while (!closed && waiting.isEmpty()) wait();
        if (closed) return null;
        var it = waiting.values().iterator();
        var task = it.next(); it.remove();
        return task;
    }
    public synchronized void retry(Task<T> task) {
        if (!closed && current.get(task.key()) == task) {
            waiting.put(task.key(), task);
            notifyAll();
        }
    }
    public synchronized void cancel(long key) {
        current.remove(key); waiting.remove(key);
    }
    public boolean isCurrent(Task<T> task) { return current.get(task.key()) == task; }
    public boolean deliver(Task<T> task, Runnable consumer) {
        synchronized (delivery) {
            if (!isCurrent(task)) return false;
            consumer.run();
            current.computeIfPresent(task.key(),(key,latest)->latest==task?null:latest);
            return true;
        }
    }
    public int size() { return current.size(); }
    @Override public synchronized void close() {
        closed = true; current.clear(); waiting.clear(); notifyAll();
    }
}
