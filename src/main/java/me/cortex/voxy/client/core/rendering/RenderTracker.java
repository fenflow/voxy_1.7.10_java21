package me.cortex.voxy.client.core.rendering;

import java.util.*;
import java.util.function.*;
import me.cortex.voxy.client.core.rendering.building.*;
import me.cortex.voxy.common.world.*;

/** Keeps the last complete LOD partition visible until all replacement leaves are ready. */
public final class RenderTracker {
    private Set<Long> wanted=new HashSet<>();
    private Set<Long> published=new HashSet<>();
    private final Map<Long,BuiltSection> staged=new HashMap<>();
    private final LongConsumer request,cancel;
    private final Consumer<List<BuiltSection>> publish;
    private RenderGenerationService renderGen;
    public RenderTracker(WorldEngine world,AbstractFarWorldRenderer<?,?> renderer){
        this.publish=renderer::enqueueResults;
        this.request=key->renderGen.enqueueTask(WorldEngine.getLevel(key),WorldEngine.getX(key),WorldEngine.getY(key),WorldEngine.getZ(key),this::shouldStillBuild);
        this.cancel=key->renderGen.removeTask(WorldEngine.getLevel(key),WorldEngine.getX(key),WorldEngine.getY(key),WorldEngine.getZ(key));
    }
    public RenderTracker(LongConsumer request,LongConsumer cancel,Consumer<List<BuiltSection>> publish){
        this.request=request;this.cancel=cancel;this.publish=publish;
    }
    public void setRenderGen(RenderGenerationService service){renderGen=service;}
    public synchronized void replaceSelection(Set<Long> selection){
        var previous=wanted;
        wanted=new HashSet<>(selection);
        for(long key:previous)if(!wanted.contains(key))cancel.accept(key);
        staged.entrySet().removeIf(entry->{if(wanted.contains(entry.getKey()))return false;entry.getValue().free();return true;});
        for(long key:wanted)if(!published.contains(key)&&!staged.containsKey(key)&&!previous.contains(key))request.accept(key);
        commitIfReady();
    }
    public synchronized void sectionUpdated(WorldSection section){
        // A neighbour may be active even when the changed section belongs to another LOD.
        rebuild(section.lvl,section.x,section.y,section.z);
        rebuild(section.lvl,section.x-1,section.y,section.z);rebuild(section.lvl,section.x+1,section.y,section.z);
        rebuild(section.lvl,section.x,section.y-1,section.z);rebuild(section.lvl,section.x,section.y+1,section.z);
        rebuild(section.lvl,section.x,section.y,section.z-1);rebuild(section.lvl,section.x,section.y,section.z+1);
    }
    private void rebuild(int level,int x,int y,int z){
        long key=WorldEngine.getWorldSectionId(level,x,y,z);
        if(wanted.contains(key)){
            var old=staged.remove(key);if(old!=null)old.free();
            request.accept(key);
        }
    }
    public synchronized void processBuildResult(BuiltSection result){
        if(!wanted.contains(result.position)){result.free();return;}
        if(published.contains(result.position)){publish.accept(List.of(result));return;}
        var old=staged.put(result.position,result);if(old!=null)old.free();
        commitIfReady();
    }
    private void commitIfReady(){
        for(long key:wanted)if(!published.contains(key)&&!staged.containsKey(key))return;
        var batch=new ArrayList<>(staged.values());
        for(long key:published)if(!wanted.contains(key))batch.add(new BuiltSection(key));
        if(!batch.isEmpty())publish.accept(batch);
        published=new HashSet<>(wanted);staged.clear();
    }
    public synchronized boolean shouldStillBuild(int level,int x,int y,int z){return wanted.contains(WorldEngine.getWorldSectionId(level,x,y,z));}
    public synchronized void close(){staged.values().forEach(BuiltSection::free);staged.clear();}
}
