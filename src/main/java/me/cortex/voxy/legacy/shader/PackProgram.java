package me.cortex.voxy.legacy.shader;

import java.util.*;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.BlendState;
import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gl.shader.ShaderType;
import net.coderbot.iris.gl.program.*;
import net.coderbot.iris.gl.state.FogMode;
import net.coderbot.iris.pipeline.DeferredWorldRenderingPipeline;
import net.coderbot.iris.pipeline.transform.*;
import net.coderbot.iris.shaderpack.ProgramSource;
import net.coderbot.iris.uniforms.CommonUniforms;
import net.coderbot.iris.uniforms.builtin.BuiltinReplacementUniforms;
import org.joml.Matrix4f;
import static me.cortex.voxy.legacy.NativeGL.*;

/** Runs the selected pack's programs with Iris uniforms, samplers and draw-buffer blend directives. */
final class PackProgram implements AutoCloseable {
    private final Shader shader;
    private final ProgramUniforms uniforms;
    private final ProgramSamplers samplers;
    private final ProgramImages images;
    private final DeferredWorldRenderingPipeline pipeline;
    private final boolean water,shadow;
    private final boolean overrideBlend;
    private final BlendState blend;
    private final Map<Integer,BlendState> bufferBlends=new HashMap<>();
    PackProgram(ProgramSource source, DeferredWorldRenderingPipeline pipeline, boolean water,boolean distant,boolean shadow) {
        this.pipeline=pipeline;this.water=water;this.shadow=shadow;
        if(source.getGeometrySource().isPresent()||source.getTessControlSource().isPresent()||source.getTessEvalSource().isPresent())
            throw new IllegalArgumentException("Geometry/tessellation stages are not supported by the voxel adapter");
        var transformed=TransformPatcher.patchDHTerrain("voxy_"+source.getName(),
            PackShaderPatcher.prepareVertex(source.getVertexSource().orElseThrow()),null,null,null,source.getFragmentSource().orElseThrow(),pipeline.getTextureMap());
        var patched=PackShaderPatcher.adapt(transformed.get(PatchShaderType.VERTEX),transformed.get(PatchShaderType.FRAGMENT),distant,shadow);
        try {
            var folder=java.nio.file.Path.of("voxy-shader-debug");java.nio.file.Files.createDirectories(folder);
            java.nio.file.Files.writeString(folder.resolve(source.getName()+".vert"),patched.vertex());
            java.nio.file.Files.writeString(folder.resolve(source.getName()+".frag"),patched.fragment());
        } catch(java.io.IOException e){me.cortex.voxy.legacy.ForgeVoxy.LOG.warn("Could not save optional shader diagnostics",e);}
        shader=Shader.make().addSource(ShaderType.VERTEX,patched.vertex()).addSource(ShaderType.FRAGMENT,patched.fragment()).compile();
        try {
            GLStateManager.glUseProgram(shader.id());glUseProgram(shader.id());
            var ub=ProgramUniforms.builder("voxy_"+source.getName(),shader.id());
            var sb=ProgramSamplers.builder(shader.id(),Set.of(0,1,31));
            var ib=ProgramImages.builder(shader.id());
            CommonUniforms.addDynamicUniforms(ub,FogMode.PER_VERTEX);
            BuiltinReplacementUniforms.addBuiltinReplacementUniforms(ub);
            pipeline.getCustomUniforms().assignTo(ub);
            pipeline.addGbufferOrShadowSamplers(sb,ib,shadow?pipeline::getFlippedBeforeShadow:water?pipeline::getFlippedAfterTranslucent:pipeline::getFlippedAfterPrepare,shadow,false,true,false);
            pipeline.getCustomUniforms().mapholderToPass(ub,this);
            uniforms=ub.buildUniforms();samplers=sb.build();images=ib.build();
            overrideBlend=source.getDirectives().getBlendModeOverride().isPresent();
            BlendState mode=null;
            if(overrideBlend)try {
                var f=net.coderbot.iris.gl.blending.BlendModeOverride.class.getDeclaredField("blendMode");f.setAccessible(true);
                mode=(BlendState)f.get(source.getDirectives().getBlendModeOverride().get());
            }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
            blend=mode;
            int[] buffers=source.getDirectives().getDrawBuffers();
            for(var b:source.getDirectives().getBufferBlendOverrides())for(int i=0;i<buffers.length;i++)if(buffers[i]==b.getIndex())bufferBlends.put(i,b.getBlendMode());
        }catch(RuntimeException|Error e){shader.free();throw e;}
    }
    void bind(Matrix4f projection,Matrix4f view,int nearDepth) {
        GLStateManager.glUseProgram(shader.id());glUseProgram(shader.id());
        samplers.update();uniforms.update();pipeline.getCustomUniforms().push(this);images.update();
        matrix("iris_ProjectionMatrix",projection);matrix("iris_ProjectionMatrixInverse",new Matrix4f(projection).invert());
        matrix("iris_ModelViewMatrix",view);matrix("iris_ModelViewMatrixInverse",new Matrix4f(view).invert());
        glUniformMatrix3fv(glGetUniformLocation(shader.id(),"iris_NormalMatrix"),false,new org.joml.Matrix3f(view).invert().transpose().get(new float[9]));
        glUniform3f(glGetUniformLocation(shader.id(),"modelOffset"),0,0,0);
        glUniform4f(glGetUniformLocation(shader.id(),"iris_ColorModulator"),1,1,1,1);
        glUniform1f(glGetUniformLocation(shader.id(),"voxyNearRadius"),Math.max(0,net.minecraft.client.Minecraft.getMinecraft().gameSettings.renderDistanceChunks*16-16));
        glBindTextureUnit(31,nearDepth);glBindSampler(31,0);
        if(!shadow){
            if(overrideBlend){if(blend==null)glDisable(GL_BLEND);else{glEnable(GL_BLEND);glBlendFuncSeparate(blend.getSrcRgb(),blend.getDstRgb(),blend.getSrcAlpha(),blend.getDstAlpha());}}
            for(var entry:bufferBlends.entrySet()){
                int i=entry.getKey();var b=entry.getValue();
                if(b==null)glDisablei(GL_BLEND,i);else{glEnablei(GL_BLEND,i);glBlendFuncSeparatei(i,b.getSrcRgb(),b.getDstRgb(),b.getSrcAlpha(),b.getDstAlpha());}
            }
        }
    }
    private void matrix(String name,Matrix4f value){glUniformMatrix4fv(glGetUniformLocation(shader.id(),name),false,value.get(new float[16]));}
    public void close(){shader.free();}
}
