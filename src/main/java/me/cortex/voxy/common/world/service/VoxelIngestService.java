package me.cortex.voxy.common.world.service;

import java.util.concurrent.*;
import me.cortex.voxy.common.voxelization.VoxelizedSection;
import me.cortex.voxy.common.voxelization.WorldConversionFactory;
import me.cortex.voxy.common.world.WorldEngine;

/** Workers see immutable snapshots, never live Minecraft chunks or tile entities. */
public final class VoxelIngestService {
    private final ThreadPoolExecutor executor;
    private final WorldEngine world;
    private final java.util.concurrent.atomic.AtomicReference<RuntimeException> failure=new java.util.concurrent.atomic.AtomicReference<>();
    public VoxelIngestService(WorldEngine world, int workers) {
        this.world = world;
        executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(256), r -> new Thread(r, "Voxy ingest"));
    }
    private Runnable task(VoxelizedSection snapshot) {
        return () -> {
            try { WorldConversionFactory.mipSection(snapshot, world.getMapper()); world.insertUpdate(snapshot); }
            catch (RuntimeException e) { failure.compareAndSet(null,e); }
        };
    }
    public void checkFailure(){var error=failure.get();if(error!=null)throw new IllegalStateException("Terrain capture failed",error);}
    public void enqueueIngestBlocking(VoxelizedSection snapshot){
        checkFailure();
        var task=task(snapshot);
        try { executor.execute(task); }
        catch(RejectedExecutionException full){
            try {
                while(!executor.isShutdown()){
                    checkFailure();
                    if(executor.getQueue().offer(task,100,TimeUnit.MILLISECONDS))return;
                }
                throw new IllegalStateException("Ingest is shut down",full);
            } catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
        }
    }
    public boolean enqueueIngest(VoxelizedSection snapshot) {
        checkFailure();
        try {
            executor.execute(task(snapshot));
            return true;
        } catch (RejectedExecutionException e) { return false; }
    }
    public int getTaskCount() { return executor.getQueue().size() + executor.getActiveCount(); }
    public void shutdown() {
        executor.shutdown();
        try { if (!executor.awaitTermination(60, TimeUnit.SECONDS)) throw new IllegalStateException("Voxy ingest did not drain"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
        checkFailure();
    }
}
