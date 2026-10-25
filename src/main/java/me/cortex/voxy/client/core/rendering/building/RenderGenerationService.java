package me.cortex.voxy.client.core.rendering.building;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import me.cortex.voxy.client.core.model.*;
import me.cortex.voxy.common.util.LatestTaskQueue;
import me.cortex.voxy.common.world.*;

public final class RenderGenerationService {
    public interface TaskChecker {boolean check(int lvl,int x,int y,int z);}
    private record Build(int level,int x,int y,int z,TaskChecker checker){}
    private final LatestTaskQueue<Build> tasks=new LatestTaskQueue<>();
    private final AtomicReference<RuntimeException> failure=new AtomicReference<>();
    private final Thread[] workers;
    private final WorldEngine world;
    private final ModelManager models;
    private final Consumer<BuiltSection> consumer;
    private final boolean meshlets;
    private volatile boolean running=true;
    public RenderGenerationService(WorldEngine world,ModelManager models,int count,Consumer<BuiltSection> consumer,boolean meshlets){
        this.world=world;this.models=models;this.consumer=consumer;this.meshlets=meshlets;
        workers=new Thread[count];
        for(int i=0;i<count;i++){workers[i]=new Thread(this::work,"Voxy mesh "+i);workers[i].setDaemon(true);workers[i].start();}
    }
    private void work(){
        var factory=new RenderDataFactory(world,models,meshlets);
        while(running){
            try{
                var task=tasks.take();if(task==null)return;
                var b=task.value();
                if(!tasks.isCurrent(task)||!b.checker.check(b.level,b.x,b.y,b.z))continue;
                WorldSection section=world.acquireIfExists(b.level,b.x,b.y,b.z);
                BuiltSection result;
                try{result=section==null?new BuiltSection(task.key()):factory.generateMesh(section);}
                catch(IdNotYetComputedException pending){Thread.sleep(10);tasks.retry(task);continue;}
                finally{if(section!=null)section.release();}
                if(!tasks.deliver(task,()->consumer.accept(result)))result.free();
            }catch(InterruptedException stop){if(running)Thread.currentThread().interrupt();return;}
            catch(RuntimeException error){failure.compareAndSet(null,error);return;}
        }
    }
    public void enqueueTask(int level,int x,int y,int z,TaskChecker checker){tasks.enqueue(WorldEngine.getWorldSectionId(level,x,y,z),new Build(level,x,y,z,checker));}
    public void removeTask(int level,int x,int y,int z){tasks.cancel(WorldEngine.getWorldSectionId(level,x,y,z));}
    public void checkFailure(){var error=failure.get();if(error!=null)throw new IllegalStateException("Terrain mesh failed",error);}
    public int getTaskCount(){return tasks.size();}
    public void shutdown(){
        running=false;tasks.close();for(var worker:workers)worker.interrupt();
        try{for(var worker:workers)worker.join();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
    }
}
