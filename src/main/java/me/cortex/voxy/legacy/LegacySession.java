package me.cortex.voxy.legacy;

import me.cortex.voxy.legacy.NativeGL;
import static me.cortex.voxy.legacy.NativeGL.*;

import java.nio.file.Path;
import java.util.*;
import me.cortex.voxy.client.core.*;
import me.cortex.voxy.client.core.rendering.*;
import me.cortex.voxy.client.core.rendering.building.RenderGenerationService;
import me.cortex.voxy.client.core.rendering.post.PostProcessing;
import me.cortex.voxy.common.voxelization.WorldConversionFactory;
import me.cortex.voxy.common.world.WorldEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.EmptyChunk;
import org.joml.Matrix4f;

public final class LegacySession implements AutoCloseable {
    public final WorldClient clientWorld;
    private WorldEngine world;
    private Gl46FarWorldRenderer renderer;
    private Gl46Viewport viewport;
    private Gl46Viewport shadowViewport;
    private RenderGenerationService mesher;
    private RenderTracker tracker;
    private DistanceTracker distance;
    private PostProcessing post;
    private WorldObserver observer;
    private final ChunkCaptureQueue<Chunk> pending=new ChunkCaptureQueue<>();
    private final Map<Long,Chunk> seen=new HashMap<>();
    private boolean initialized;
    private int frames;
    private boolean lastShaderMode;
    private static final java.lang.reflect.Field CLIENT_CHUNKS = cpw.mods.fml.relauncher.ReflectionHelper.findField(
        net.minecraft.client.multiplayer.ChunkProviderClient.class,"chunkListing","field_73237_c");
    private static final java.lang.reflect.Field CLIENT_CHUNK_MAP = cpw.mods.fml.relauncher.ReflectionHelper.findField(
        net.minecraft.client.multiplayer.ChunkProviderClient.class,"chunkMapping","field_73236_b");

    public LegacySession(WorldClient clientWorld,Path cache) throws java.io.IOException {
        this.clientWorld=clientWorld;
        var storage=new FileStorageBackend(cache);
        try {
        world=new WorldEngine(storage,1,1,5);
        Capabilities.init();
        renderer=new Gl46FarWorldRenderer(LegacyConfig.geometryMiB*1024*1024/8,400000);
        viewport=renderer.createViewport();
        shadowViewport=renderer.createViewport();
        tracker=new RenderTracker(world,renderer);
        mesher=new RenderGenerationService(world,renderer.getModelManager(),LegacyConfig.threads,tracker::processBuildResult,false);
        tracker.setRenderGen(mesher);world.setDirtyCallback(tracker::sectionUpdated);
        int q=LegacyConfig.quality;
        distance=new DistanceTracker(tracker,new int[]{q,q,q,q},(LegacyConfig.distance+1)/2,0,7);
        post=new PostProcessing();
        world.getMapper().setCallbacks(renderer::addBlockState,renderer::addBiome);
        world.getMapper().getBiomeEntries().forEach(renderer::addBiome);
        world.getMapper().getStateEntries().forEach(renderer::addBlockState);
        observer=new WorldObserver(this);clientWorld.addWorldAccess(observer);
        } catch (RuntimeException | LinkageError e) {
            try { close(); } catch (RuntimeException cleanup) { e.addSuppressed(cleanup); }
            if (world == null) storage.close();
            throw e;
        }
    }
    private static long chunkKey(int x,int z){return ((long)x<<32)|(z&0xFFFFFFFFL);}
    public void dirty(int x,int y,int z){
        if(y<0||y>=256)return;
        var provider=clientWorld.getChunkProvider();
        if(!provider.chunkExists(x>>4,z>>4))return;
        var chunk=provider.provideChunk(x>>4,z>>4);
        if(!(chunk instanceof EmptyChunk))pending.request(chunkKey(x>>4,z>>4),chunk,1<<(y>>4));
    }
    public void loaded(Chunk chunk){
        if(chunk instanceof EmptyChunk)return;
        long key=chunkKey(chunk.xPosition,chunk.zPosition);
        seen.put(key,chunk);
        pending.request(key,chunk,0xffff);
    }
    public void unloaded(Chunk chunk){
        if(chunk instanceof EmptyChunk)return;
        long key=chunkKey(chunk.xPosition,chunk.zPosition);
        // The final received data must win over captures made during first arrival.
        // Prioritize it: vanilla geometry is disappearing at this boundary now.
        pending.departing(key,chunk);
        seen.remove(key,chunk);
    }
    private boolean capture(Chunk chunk,int section,boolean blocking){
        var snapshot=WorldConversionFactory.capture(chunk,section,world.getMapper());
        if(blocking){world.ingestService.enqueueIngestBlocking(snapshot);return true;}
        return world.ingestService.enqueueIngest(snapshot);
    }
    public void tick(){
        var mc=Minecraft.getMinecraft();if(mc.renderViewEntity==null)return;
        // Client loadChunk does not call Chunk.onChunkLoad in 1.7.10. Observe the
        // entire received set after packet processing, independent of render distance.
        // A once-per-second camera-centred scan misses chunks during fast travel.
        try {
            var live=new HashMap<Long,Chunk>();
            Object provider=clientWorld.getChunkProvider();
            for(Object value:ReceivedChunkSnapshot.copy((Collection<?>)CLIENT_CHUNKS.get(provider),CLIENT_CHUNK_MAP.get(provider))){
                Chunk chunk=(Chunk)value;
                long key=chunkKey(chunk.xPosition,chunk.zPosition);
                live.put(key,chunk);
            }
            for(var entry:new ArrayList<>(seen.entrySet()))
                if(live.get(entry.getKey())!=entry.getValue())unloaded(entry.getValue());
            for(var entry:live.entrySet())
                if(seen.get(entry.getKey())!=entry.getValue())loaded(entry.getValue());
        } catch(IllegalAccessException e){throw new IllegalStateException("Cannot observe received client chunks",e);}
        // Apply backpressure instead of allowing retained unloaded chunks to grow forever.
        while(pending.chunkCount()>512)pending.captureOne((c,y)->capture(c,y,true));
        int limit=CaptureBudget.sectionLimit(LegacyConfig.sectionsPerTick,pending.sectionCount());
        long deadline=System.nanoTime()+CaptureBudget.nanoseconds(pending.sectionCount());
        for(int i=0;i<limit&&!pending.isEmpty()&&world.ingestService.getTaskCount()<240;i++){
            if(!pending.captureOne((c,y)->capture(c,y,false)))break;
            if(System.nanoTime()>deadline)break;
        }
        world.ingestService.checkFailure();
        mesher.checkFailure();
        world.savingService.checkFailure();
    }
    public void render(float partial){
        boolean shaderMode=AngelicaCompat.shadersActive();
        if(shaderMode!=lastShaderMode){lastShaderMode=shaderMode;frames=0;}
        var mc=Minecraft.getMinecraft();var camera=mc.renderViewEntity;
        double x=camera.lastTickPosX+(camera.posX-camera.lastTickPosX)*partial;
        double y=camera.lastTickPosY+(camera.posY-camera.lastTickPosY)*partial;
        double z=camera.lastTickPosZ+(camera.posZ-camera.lastTickPosZ)*partial;
        if(!initialized){distance.init((int)Math.floor(x),(int)Math.floor(z));initialized=true;}
        distance.setCenter((int)Math.floor(x),(int)Math.floor(y),(int)Math.floor(z));
        Matrix4f original=AngelicaCompat.projection(),view=AngelicaCompat.modelView();
        Matrix4f projection=new Matrix4f(original);
        float near=16,far=Math.max(512,LegacyConfig.distance*16+512);
        projection.m22(-(far+near)/(far-near)).m32(-(2*far*near)/(far-near));
        renderer.setupRender(new Matrix4f(projection).mul(view),x,y,z);
        int[] size=new int[4];glGetIntegerv(GL_VIEWPORT,size);
        viewport.setProjection(projection).setModelView(view).setCamera(x,y,z).setScreenSize(size[2],size[3]);
        int target=glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        post.setup(size[2],size[3],target);
        renderer.renderFarAwayOpaque(viewport);
        post.computeSSAO(projection,view);
        renderer.renderFarAwayTranslucent(viewport);
        post.renderPost(projection,original,target);
        if (frames++ == 120) {
            int error = glGetError();
            ForgeVoxy.LOG.info("Voxy rendering check: GL error {}, shaders {}, camera {}, {}, {}, model-view translation {}, {}, {}",
                error,shaderMode,x,y,z,view.m30(),view.m31(),view.m32());
        }
    }
    public void debug(List<String> lines){
        lines.add("Voxy legacy: capture "+pending.sectionCount()+", ingest "+world.ingestService.getTaskCount()+", mesh "+mesher.getTaskCount());
        renderer.addDebugData(lines);
    }
    public void renderShader(int pass) {
        var bridge=me.cortex.voxy.legacy.shader.ShaderBridge.current();
        // Match the origin used by Angelica's pack uniforms and shadow matrices, including eye height.
        var camera=com.gtnewhorizons.angelica.rendering.RenderingState.INSTANCE.getCameraPosition();
        double x=camera.x,y=camera.y,z=camera.z;
        if(!initialized){distance.init((int)Math.floor(x),(int)Math.floor(z));initialized=true;}
        distance.setCenter((int)Math.floor(x),(int)Math.floor(y),(int)Math.floor(z));
        var projection=pass==2?new Matrix4f(net.coderbot.iris.pipeline.ShadowRenderer.PROJECTION):bridge.projectionMatrix();
        var view=pass==2?new Matrix4f(net.coderbot.iris.pipeline.ShadowRenderer.MODELVIEW):new Matrix4f(com.gtnewhorizons.angelica.rendering.RenderingState.INSTANCE.getModelViewMatrix());
        var vp=pass==2?shadowViewport:viewport;
        int[] size=new int[4];glGetIntegerv(GL_VIEWPORT,size);
        vp.setProjection(projection).setModelView(view).setCamera(x,y,z).setScreenSize(size[2],size[3]);
        if(pass!=1)renderer.setupRender(new Matrix4f(projection).mul(view),x,y,z);
        if(pass==2)renderer.renderShadow(vp,()->bridge.bindShadow());
        else if(pass==0){bridge.bindTarget(false);renderer.renderFarAwayOpaque(vp,()->bridge.bindProgram(false,projection,view));bridge.copyOpaqueDepth();}
        else {bridge.bindTarget(true);renderer.renderFarAwayTranslucent(vp,()->bridge.bindProgram(true,projection,view));}
    }
    public void close(){
        if(observer!=null)clientWorld.removeWorldAccess(observer);
        if(world!=null){
            world.setDirtyCallback(null);

        }
        var cleanup=new ArrayList<Runnable>();
        if(world!=null)cleanup.add(()->{while(!pending.isEmpty())pending.captureOne((c,y)->capture(c,y,true));});
        if(mesher!=null)cleanup.add(mesher::shutdown);
        if(tracker!=null)cleanup.add(tracker::close);
        if(world!=null)cleanup.add(world::shutdown);
        if(renderer!=null)cleanup.add(me.cortex.voxy.client.core.rendering.util.UploadStream.INSTANCE::commit);
        if(viewport!=null)cleanup.add(viewport::delete);
        if(shadowViewport!=null)cleanup.add(shadowViewport::delete);
        if(renderer!=null)cleanup.add(renderer::shutdown);
        if(post!=null)cleanup.add(post::shutdown);
        RuntimeException failure=null;
        for(Runnable action:cleanup){
            try{action.run();}catch(RuntimeException e){if(failure==null)failure=e;else failure.addSuppressed(e);}
        }
        seen.clear();
        if(failure!=null)throw failure;
    }
}
