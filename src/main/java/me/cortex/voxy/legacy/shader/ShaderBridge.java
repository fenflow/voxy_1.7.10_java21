package me.cortex.voxy.legacy.shader;

import java.util.*;
import me.cortex.voxy.legacy.*;
import net.coderbot.iris.Iris;
import net.coderbot.iris.pipeline.DeferredWorldRenderingPipeline;
import net.coderbot.iris.gl.framebuffer.GlFramebuffer;
import net.coderbot.iris.gl.program.*;
import net.coderbot.iris.shaderpack.*;
import net.coderbot.iris.shaderpack.loading.ProgramId;
import net.coderbot.iris.rendertarget.RenderTargets;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.rendering.RenderingState;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import static me.cortex.voxy.legacy.NativeGL.*;

/** Capability-based shader adapter; no shader product or version allowlist. */
public final class ShaderBridge implements AutoCloseable {
    private static ShaderBridge current;
    private static Object failedPipeline;
    private static boolean distantInterface;
    private static String failure;
    private final DeferredWorldRenderingPipeline pipeline;
    private PackProgram terrain,water,shadowProgram;
    private GlFramebuffer terrainTarget,waterTarget;
    private RenderTargets targets;
    private int depth,opaqueDepth,width,height;
    private int nearMask;
    private me.cortex.voxy.client.core.gl.shader.Shader depthCopy;
    private boolean ready,checkTargets,distant;
    private int renderedFrames,shadowPasses;
    private ShaderBridge(DeferredWorldRenderingPipeline pipeline){this.pipeline=pipeline;}
    public static ShaderBridge current(){return current;}
    public static boolean active(){return current!=null&&current.ready&&enabled();}
    private static boolean enabled(){return LegacyConfig.enabled&&LegacyConfig.shaderCompatibility&&Iris.getIrisConfig()!=null&&Iris.getIrisConfig().areShadersEnabled()&&Iris.getIrisConfig().getShaderPackName().isPresent();}
    public static String status(){return active()?"Voxy active: shader-pack lighting ("+(current.distant?"distant terrain interface":"generic terrain adapter")+")":failure==null?"Voxy waiting for shader integration":"Voxy shader integration unavailable: "+failure;}
    public static Iterable<StringPair> defines(Iterable<StringPair> original){
        distantInterface=false;failure=null;
        if(!enabled())return original;
        try{distantInterface=PackCapabilities.hasDistantTerrain(Minecraft.getMinecraft().mcDataDir.toPath().resolve("shaderpacks").resolve(Iris.getIrisConfig().getShaderPackName().orElseThrow()));}
        catch(java.io.IOException e){ForgeVoxy.LOG.warn("Cannot inspect selected shader pack",e);}
        var result=new ArrayList<StringPair>();original.forEach(result::add);
        if(distantInterface)result.add(new StringPair("DISTANT_HORIZONS",""));
        result.add(new StringPair("VOXY_LEGACY","1"));return result;
    }
    public static float cameraFarPlane(float original){return enabled()&&!distantInterface?Math.max(original,LegacyConfig.distance*16+512):original;}
    public static int cameraDistance(int original){return enabled()&&!distantInterface?Math.max(original,LegacyConfig.distance*16):original;}
    public static Matrix4fc projection(Matrix4fc original){
        if(!enabled()||!distantInterface)return original;
        var matrix=new Matrix4f(RenderingState.INSTANCE.getProjectionMatrix());
        float n=16,f=farPlane(0);return matrix.m22(-(f+n)/(f-n)).m32(-2*f*n/(f-n));
    }
    public Matrix4f projectionMatrix(){return new Matrix4f(projection(RenderingState.INSTANCE.getProjectionMatrix()));}
    public static float farPlane(float original){return enabled()&&distantInterface?Math.max(512,LegacyConfig.distance*16+512):original;}
    public static float nearPlane(float original){return enabled()&&distantInterface?16:original;}
    public static int distance(int original){return enabled()&&distantInterface?LegacyConfig.distance*16:original;}
    public static int depth(int original){return current!=null&&enabled()&&distantInterface?current.depth:original;}
    public static int opaqueDepth(int original){return current!=null&&enabled()&&distantInterface?current.opaqueDepth:original;}
    public static void beginFrame(Object value){
        if(!enabled()||failedPipeline==value)return;
        try(var state=new ShaderState()){
            if(current==null||current.pipeline!=value){
                if(current!=null)current.close();
                var next=new ShaderBridge((DeferredWorldRenderingPipeline)value);current=next;
                next.width=1;next.height=1;next.depth=next.createDepth();next.opaqueDepth=next.createDepth();next.clearDepth();
                next.initialize();next.ready=true;
                ForgeVoxy.LOG.info("Voxy shader adapter initialized for {}: {}, pack shadow vertex {}",Iris.getIrisConfig().getShaderPackName().orElse("?"),next.distant?"distant terrain interface":"generic terrain",next.shadowProgram!=null);
            }
            current.resize();current.clearDepth();
        }catch(RuntimeException|LinkageError e){
            failedPipeline=value;failure=e.getMessage();ForgeVoxy.LOG.error("Voxy shader integration failed",e);
            if(current!=null){current.ready=false;current.clearDepth();}
        }
    }
    public static void opaque(Object value){render(value,0);}
    public static void translucent(Object value){render(value,1);}
    public static void shadow(){if(current!=null&&current.shadowProgram!=null)render(current.pipeline,2);}
    private static void render(Object value,int pass){
        if(!active()||current.pipeline!=value)return;
        try(var state=new ShaderState()){
            int query=pass==2&&current.shadowPasses==599?glGenQueries():0;
            if(query!=0)glBeginQuery(GL_SAMPLES_PASSED,query);
            try{LegacyClient.shaderRender(pass);}
            finally{if(query!=0){glEndQuery(GL_SAMPLES_PASSED);ForgeVoxy.LOG.info("Voxy pack shadow check: {} depth samples passed",glGetQueryObjectui(query,GL_QUERY_RESULT));glDeleteQueries(query);}}
            if(pass==2)current.shadowPasses++;
            if(pass==1&&++current.renderedFrames==600){
                float[] samples=new float[current.width*current.height];
                glGetTextureImage(current.distant?current.depth:current.targets.getDepthTexture(),0,GL_DEPTH_COMPONENT,GL_FLOAT,samples);
                int occupied=0;for(float d:samples)if(d<1)occupied++;
                ForgeVoxy.LOG.info("Voxy pack rendering check: GL error {}, terrain depth pixels {}, shadow passes {}, projection far {}",glGetError(),occupied,current.shadowPasses,RenderingState.INSTANCE.getProjectionMatrix().perspectiveFar());
            }
        }
    }
    public static void destroy(Object value){if(current!=null&&current.pipeline==value){current.close();current=null;}if(failedPipeline==value)failedPipeline=null;}
    private void initialize(){
        try{
            var f=DeferredWorldRenderingPipeline.class.getDeclaredField("renderTargets");f.setAccessible(true);targets=(RenderTargets)f.get(pipeline);
            var r=DeferredWorldRenderingPipeline.class.getDeclaredField("resolver");r.setAccessible(true);var resolver=(ProgramFallbackResolver)r.get(pipeline);
            distant=pipeline.getDHTerrainShader().isPresent();
            if(!distant)depthCopy=me.cortex.voxy.client.core.gl.shader.Shader.make().add(me.cortex.voxy.client.core.gl.shader.ShaderType.COMPUTE,"voxy:post/copy_depth.comp").compile();
            var source=(distant?pipeline.getDHTerrainShader():resolver.resolve(ProgramId.Terrain)).orElseThrow();
            var waterSource=(distant?pipeline.getDHWaterShader():resolver.resolve(ProgramId.Water)).orElse(source);
            terrain=new PackProgram(source,pipeline,false,distant,false);water=new PackProgram(waterSource,pipeline,true,distant,false);
            terrainTarget=pipeline.createDHFramebuffer(source,false);waterTarget=pipeline.createDHFramebuffer(waterSource,true);
            var shadowSource=pipeline.getDHShadowShader().or(()->resolver.resolve(ProgramId.Shadow));
            if(shadowSource.isPresent()&&pipeline.hasShadowRenderTargets())try{shadowProgram=new PackProgram(shadowSource.get(),pipeline,false,false,true);}
            catch(RuntimeException e){ForgeVoxy.LOG.warn("Pack shadow vertex adapter unavailable; distant terrain still receives pack lighting",e);}
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private int createDepth(){
        int texture=glCreateTextures(GL_TEXTURE_2D);glTextureStorage2D(texture,1,GL_DEPTH_COMPONENT32F,width,height);
        glTextureParameteri(texture,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTextureParameteri(texture,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
        glTextureParameteri(texture,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTextureParameteri(texture,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);return texture;
    }
    private void resize(){
        var mc=Minecraft.getMinecraft();int w=mc.getFramebuffer().framebufferWidth,h=mc.getFramebuffer().framebufferHeight;
        if(width==w&&height==h)return;
        if(depth!=0)glDeleteTextures(depth);if(opaqueDepth!=0)glDeleteTextures(opaqueDepth);
        width=w;height=h;depth=createDepth();opaqueDepth=createDepth();checkTargets=true;
        if(nearMask!=0)glDeleteTextures(nearMask);
        if(!distant){nearMask=glCreateTextures(GL_TEXTURE_2D);glTextureStorage2D(nearMask,1,GL_R32F,width,height);glTextureParameteri(nearMask,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTextureParameteri(nearMask,GL_TEXTURE_MAG_FILTER,GL_NEAREST);}
    }
    private void clearDepth(){glClearTexImage(depth,0,GL_DEPTH_COMPONENT,GL_FLOAT,new float[]{1});glClearTexImage(opaqueDepth,0,GL_DEPTH_COMPONENT,GL_FLOAT,new float[]{1});}
    public void bindTarget(boolean trans){
        if(!distant&&!trans){
            // Snapshot the near-world mask before writing into its shared depth buffer.
            // Sampling that same attachment during drawing would be a feedback loop.
            depthCopy.bind();glBindTextureUnit(0,targets.getDepthTexture());glBindSampler(0,0);
            glBindImageTexture(0,nearMask,0,false,0,GL_WRITE_ONLY,GL_R32F);
            glDispatchCompute((width+15)/16,(height+15)/16,1);
            glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT|GL_TEXTURE_FETCH_BARRIER_BIT);
        }
        int attachment=distant?depth:targets.getDepthTexture();
        glNamedFramebufferTexture(terrainTarget.getId(),GL_DEPTH_ATTACHMENT,attachment,0);
        glNamedFramebufferTexture(waterTarget.getId(),GL_DEPTH_ATTACHMENT,attachment,0);
        if(distant){glNamedFramebufferTexture(terrainTarget.getId(),GL_STENCIL_ATTACHMENT,0,0);glNamedFramebufferTexture(waterTarget.getId(),GL_STENCIL_ATTACHMENT,0,0);}
        if(checkTargets){
            if(glCheckNamedFramebufferStatus(terrainTarget.getId(),GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE||glCheckNamedFramebufferStatus(waterTarget.getId(),GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Incomplete Voxy pack framebuffer");
            checkTargets=false;
        }
        glBindFramebuffer(GL_FRAMEBUFFER,(trans?waterTarget:terrainTarget).getId());glViewport(0,0,width,height);
    }
    public void bindProgram(boolean trans,Matrix4f projection,Matrix4f view){(trans?water:terrain).bind(projection,view,!distant?nearMask:trans?targets.getDepthTextureNoTranslucents().getTextureId():targets.getDepthTexture());}
    public void bindShadow(){shadowProgram.bind(new Matrix4f(net.coderbot.iris.pipeline.ShadowRenderer.PROJECTION),new Matrix4f(net.coderbot.iris.pipeline.ShadowRenderer.MODELVIEW),0);}
    public void copyOpaqueDepth(){if(distant)glCopyImageSubData(depth,GL_TEXTURE_2D,0,0,0,0,opaqueDepth,GL_TEXTURE_2D,0,0,0,0,width,height,1);}
    public void close(){
        if(terrain!=null)terrain.close();if(water!=null)water.close();if(shadowProgram!=null)shadowProgram.close();
        if(depth!=0)glDeleteTextures(depth);if(opaqueDepth!=0)glDeleteTextures(opaqueDepth);
        if(nearMask!=0)glDeleteTextures(nearMask);if(depthCopy!=null)depthCopy.free();
    }
    private static final class ShaderState implements AutoCloseable {
        private final int program=glGetInteger(GL_CURRENT_PROGRAM),active=glGetInteger(GL_ACTIVE_TEXTURE);
        private static final int[] TYPES={GL_TEXTURE_2D,GL_TEXTURE_3D,GL_TEXTURE_2D_ARRAY,GL_TEXTURE_CUBE_MAP};
        private static final int[] BINDINGS={GL_TEXTURE_BINDING_2D,GL_TEXTURE_BINDING_3D,GL_TEXTURE_BINDING_2D_ARRAY,GL_TEXTURE_BINDING_CUBE_MAP};
        private final int[][] textures=new int[32][4],images=new int[8][6];
        private final int[] samplers=new int[32];
        private final int[][] blend=new int[8][7];
        private final GlStateFrame frame;
        ShaderState(){
            int[] blendQueries={GL_BLEND_SRC_RGB,GL_BLEND_DST_RGB,GL_BLEND_SRC_ALPHA,GL_BLEND_DST_ALPHA,GL_BLEND_EQUATION_RGB,GL_BLEND_EQUATION_ALPHA};
            for(int i=0;i<8;i++){blend[i][0]=glIsEnabledi(GL_BLEND,i)?1:0;for(int j=0;j<6;j++)blend[i][j+1]=glGetIntegeri(blendQueries[j],i);}
            for(int i=0;i<32;i++){glActiveTexture(GL_TEXTURE0+i);for(int j=0;j<4;j++)textures[i][j]=glGetInteger(BINDINGS[j]);samplers[i]=glGetIntegeri(GL_SAMPLER_BINDING,i);}
            int[] queries={GL_IMAGE_BINDING_NAME,GL_IMAGE_BINDING_LEVEL,GL_IMAGE_BINDING_LAYERED,GL_IMAGE_BINDING_LAYER,GL_IMAGE_BINDING_ACCESS,GL_IMAGE_BINDING_FORMAT};
            for(int i=0;i<8;i++)for(int j=0;j<6;j++)images[i][j]=glGetIntegeri(queries[j],i);
            glActiveTexture(active);frame=new GlStateFrame();
        }
        public void close(){
            ProgramUniforms.clearActiveUniforms();ProgramSamplers.clearActiveSamplers();
            GLStateManager.glUseProgram(program);
            for(int i=0;i<32;i++){GLStateManager.glActiveTexture(GL_TEXTURE0+i);for(int j=0;j<4;j++)GLStateManager.glBindTexture(TYPES[j],textures[i][j]);glBindSampler(i,samplers[i]);}
            for(int i=0;i<8;i++){int[] b=images[i];glBindImageTexture(i,b[0],b[1],b[2]!=0,b[3],b[4],b[5]);}
            GLStateManager.glActiveTexture(active);frame.close();
            for(int i=0;i<8;i++){int[] b=blend[i];if(b[0]!=0)glEnablei(GL_BLEND,i);else glDisablei(GL_BLEND,i);glBlendFuncSeparatei(i,b[1],b[2],b[3],b[4]);glBlendEquationSeparatei(i,b[5],b[6]);}
        }
    }
}
