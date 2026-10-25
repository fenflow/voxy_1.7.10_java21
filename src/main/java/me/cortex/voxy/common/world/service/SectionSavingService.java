package me.cortex.voxy.common.world.service;

import java.util.concurrent.*;
import me.cortex.voxy.common.world.*;
import org.lwjgl.system.MemoryUtil;

public final class SectionSavingService {
    private final ThreadPoolExecutor executor;
    private final WorldEngine world;
    private final java.util.concurrent.atomic.AtomicReference<RuntimeException> failure=new java.util.concurrent.atomic.AtomicReference<>();
    public void checkFailure(){var error=failure.get();if(error!=null)throw new IllegalStateException("Voxy cache save failed",error); }
    public SectionSavingService(WorldEngine world, int workers) {
        this.world = world;
        executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> new Thread(r, "Voxy cache writer"));
    }
    public void enqueueSave(WorldSection section) {
        if (section.inSaveQueue.getAndSet(true)) return;
        section.acquire();
        try { executor.execute(() -> {
            section.inSaveQueue.set(false);
            java.nio.ByteBuffer buffer = null;
            try { buffer = SaveLoadSystem.serialize(section); world.storage.setSectionData(section.key, buffer); }
            catch (RuntimeException e) { failure.compareAndSet(null,e); }
            finally { if (buffer != null) MemoryUtil.memFree(buffer); section.release(); }
        }); } catch (RejectedExecutionException e) { section.inSaveQueue.set(false); section.release(); throw e; }
    }
    public int getTaskCount() { return executor.getQueue().size() + executor.getActiveCount(); }
    public void shutdown() {
        executor.shutdown();
        try { if (!executor.awaitTermination(60, TimeUnit.SECONDS)) throw new IllegalStateException("Voxy cache writer did not drain"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
        checkFailure();
    }
}
