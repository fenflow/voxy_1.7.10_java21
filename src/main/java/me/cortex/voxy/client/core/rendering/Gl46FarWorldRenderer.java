package me.cortex.voxy.client.core.rendering;

import me.cortex.voxy.legacy.NativeGL;
import static me.cortex.voxy.legacy.NativeGL.*;

import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gl.shader.ShaderType;
import me.cortex.voxy.client.core.rendering.util.DownloadStream;
import me.cortex.voxy.client.core.rendering.util.UploadStream;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.util.List;


public class Gl46FarWorldRenderer extends AbstractFarWorldRenderer<Gl46Viewport, DefaultGeometryManager> {
    private final Shader commandGen = Shader.make()
            .define("OPAQUE_COMMAND_CAPACITY", this.maxSections * 7)
            .add(ShaderType.COMPUTE, "voxy:lod/gl46/cmdgen.comp")
            .compile();

    private final Shader lodShader = Shader.make()
            .add(ShaderType.VERTEX, "voxy:lod/gl46/quads2.vert")
            .add(ShaderType.FRAGMENT, "voxy:lod/gl46/quads.frag")
            .compile();


    //TODO: Note the cull shader needs a different element array since its rastering cubes not quads
    private final Shader cullShader = Shader.make()
            .add(ShaderType.VERTEX, "voxy:lod/gl46/cull/raster.vert")
            .add(ShaderType.FRAGMENT, "voxy:lod/gl46/cull/raster.frag")
            .compile();

    private final GlBuffer glCommandBuffer;
    private final GlBuffer glCommandCountBuffer;

    public Gl46FarWorldRenderer(int geometryBuffer, int maxSections) {
        super(new DefaultGeometryManager(geometryBuffer*8L, maxSections));
        this.glCommandBuffer = new GlBuffer(maxSections*5L*4 * 8);
        this.glCommandCountBuffer = new GlBuffer(4*2);
        nglClearNamedBufferData(this.glCommandBuffer.id, GL_R32UI, GL_RED_INTEGER, GL_UNSIGNED_INT, 0);
    }

    protected void bindResources(Gl46Viewport viewport) {
        glBindBufferBase(GL_UNIFORM_BUFFER, 0, this.uniformBuffer.id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, this.geometry.geometryId());
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, this.glCommandBuffer.id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, this.glCommandCountBuffer.id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 4, this.geometry.metaId());
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 5, viewport.visibilityBuffer.id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 6, this.models.getBufferId());
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 7, this.models.getColourBufferId());
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 8, this.lightDataBuffer.id);//Lighting LUT
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, this.glCommandBuffer.id);
        glBindBuffer(GL_PARAMETER_BUFFER_ARB, this.glCommandCountBuffer.id);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, SharedIndexBuffer.INSTANCE.id());

        //Bind the texture atlas
        glBindSampler(0, this.models.getSamplerId());
        glBindTextureUnit(0, this.models.getTextureId());
    }

    //FIXME: dont do something like this as it breaks multiviewport mods
    // the issue is that voxy expects the counter to be incremented by one each frame (the compute shader generating the commands)
    // checks `frameId - 1`, this means in a multiviewport, effectivly only the stuff that was marked visible by the last viewport
    // would be visible in the current viewport (if there are 2 viewports)

    //To fix the issue, need to make a viewport api, that independently tracks the frameId and has its own glVisibilityBuffer buffer
    private void updateUniformBuffer(Gl46Viewport viewport) {
        long ptr = UploadStream.INSTANCE.upload(this.uniformBuffer, 0, this.uniformBuffer.size());

        var mat = new Matrix4f(viewport.projection).mul(viewport.modelView);
        var innerTranslation = new Vector3f((float) (viewport.cameraX-(this.sx<<5)), (float) (viewport.cameraY-(this.sy<<5)), (float) (viewport.cameraZ-(this.sz<<5)));
        mat.translate(-innerTranslation.x, -innerTranslation.y, -innerTranslation.z);
        mat.getToAddress(ptr); ptr += 4*4*4;
        MemoryUtil.memPutInt(ptr, this.sx); ptr += 4;
        MemoryUtil.memPutInt(ptr, this.sy); ptr += 4;
        MemoryUtil.memPutInt(ptr, this.sz); ptr += 4;
        MemoryUtil.memPutInt(ptr, this.geometry.getSectionCount()); ptr += 4;
        for (int i = 0; i < 6; i++) { new Matrix4f(viewport.projection).mul(viewport.modelView).frustumPlane(i, new org.joml.Vector4f()).getToAddress(ptr); ptr += 16; }
        innerTranslation.getToAddress(ptr); ptr += 4*3;
        MemoryUtil.memPutInt(ptr, viewport.frameId++); ptr += 4;
    }

    public void renderFarAwayOpaque(Gl46Viewport viewport) {
        renderFarAwayOpaque(viewport,null);
    }
    public void renderFarAwayOpaque(Gl46Viewport viewport,Runnable packProgram) {
        if (this.geometry.getSectionCount() == 0) {
            return;
        }

        {//Mark all of the updated sections as being visible from last frame
            for (int id : this.updatedSectionIds) {
                long ptr = UploadStream.INSTANCE.upload(viewport.visibilityBuffer, id * 4L, 4);
                MemoryUtil.memPutInt(ptr, viewport.frameId - 1);//(visible from last frame)
            }
        }

        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);


        //this.models.bakery.renderFaces(Blocks.WATER.getDefaultState().with(FluidBlock.LEVEL, 1), 1234, true);
        //this.models.bakery.renderFaces(Blocks.CHEST.getDefaultState(), 1234, false);


        glDisable(GL_BLEND); glEnable(GL_DEPTH_TEST); glDepthMask(true);
        //RenderSystem.enableBlend();
        //RenderSystem.defaultBlendFunc();

        this.updateUniformBuffer(viewport);
        // Stage the first index upload before submitting the frame.
        SharedIndexBuffer.INSTANCE.id();
        UploadStream.INSTANCE.commit();
        glBindVertexArray(AbstractFarWorldRenderer.STATIC_VAO);

        nglClearNamedBufferData(this.glCommandCountBuffer.id, GL_R32UI, GL_RED_INTEGER, GL_UNSIGNED_INT, 0);
        this.commandGen.bind();
        this.bindResources(viewport);
        glDispatchCompute((this.geometry.getSectionCount()+127)/128, 1, 1);
        glMemoryBarrier(GL_COMMAND_BARRIER_BIT | GL_SHADER_STORAGE_BARRIER_BIT | GL_UNIFORM_BARRIER_BIT);

        this.lodShader.bind();
        this.bindResources(viewport);
        if(packProgram!=null)packProgram.run();
        glDisable(GL_CULL_FACE);
        //glPointSize(10);
        //TODO: replace glMultiDrawElementsIndirectCount with glMultiDrawElementsIndirect on intel gpus, since it performs so much better
        //glMultiDrawElementsIndirect(GL_TRIANGLES, GL_UNSIGNED_INT, 0, drawCnt, 0);
        //glLineWidth(2);
        glMultiDrawElementsIndirectCount(GL_TRIANGLES, GL_UNSIGNED_INT, 0, 0, this.geometry.getSectionCount()*7, 0);
        glEnable(GL_CULL_FACE);


        /*
        DownloadStream.INSTANCE.download(this.glCommandCountBuffer, 0, 4, (ptr, siz) -> {
            int cnt = MemoryUtil.memGetInt(ptr);
            drawCnt = cnt;
        });
        DownloadStream.INSTANCE.commit();

         */



        glMemoryBarrier(GL_PIXEL_BUFFER_BARRIER_BIT | GL_FRAMEBUFFER_BARRIER_BIT);

        this.cullShader.bind();
        this.bindResources(viewport);

        glColorMask(false, false, false, false);
        glDepthMask(false);

        glDrawElementsInstanced(GL_TRIANGLES, 6 * 2 * 3, GL_UNSIGNED_BYTE, SharedIndexBuffer.CUBE_OFFSET, this.geometry.getSectionCount());

        glDepthMask(true);
        glColorMask(true, true, true, true);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);


        //TODO: need to do temporal rasterization here

        glBindVertexArray(0);
        glBindSampler(0, 0);
        glBindTextureUnit(0, 0);

    }

    @Override
    public void renderFarAwayTranslucent(Gl46Viewport viewport) {
        renderFarAwayTranslucent(viewport,null);
    }
    public void renderFarAwayTranslucent(Gl46Viewport viewport,Runnable packProgram) {
        glEnable(GL_DEPTH_TEST); glDepthMask(true);
        glBindVertexArray(AbstractFarWorldRenderer.STATIC_VAO);
        glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);

        //TODO: maybe change this so the alpha isnt applied in the same way or something?? since atm the texture bakery uses a very hacky
        // blend equation to make it avoid double applying translucency
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);


        glBindSampler(0, this.models.getSamplerId());
        glBindTextureUnit(0, this.models.getTextureId());

        //RenderSystem.blendFunc(GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE);
        this.lodShader.bind();
        this.bindResources(viewport);

        if(packProgram!=null)packProgram.run();

        glMultiDrawElementsIndirectCount(GL_TRIANGLES, GL_UNSIGNED_INT, (long)this.maxSections * 7 * 4 * 5, 4, this.geometry.getSectionCount(), 0);

        glEnable(GL_CULL_FACE);
        glBindVertexArray(0);


        glBindSampler(0, 0);
        glBindTextureUnit(0, 0);
        glDisable(GL_BLEND);


    }

    protected Gl46Viewport createViewport0() {
        return new Gl46Viewport(this);
    }

    private Shader shadowCommands;
    /** Shadow casters cannot use camera occlusion or camera-facing face selection. */
    public void renderShadow(Gl46Viewport viewport,Runnable program) {
        if(geometry.getSectionCount()==0)return;
        if(shadowCommands==null)shadowCommands=Shader.make().define("VOXY_SHADOW").define("OPAQUE_COMMAND_CAPACITY",maxSections*7)
            .add(ShaderType.COMPUTE,"voxy:lod/gl46/cmdgen.comp").compile();
        updateUniformBuffer(viewport);SharedIndexBuffer.INSTANCE.id();UploadStream.INSTANCE.commit();
        glBindVertexArray(AbstractFarWorldRenderer.STATIC_VAO);
        nglClearNamedBufferData(glCommandCountBuffer.id,GL_R32UI,GL_RED_INTEGER,GL_UNSIGNED_INT,0);
        shadowCommands.bind();bindResources(viewport);
        glDispatchCompute((geometry.getSectionCount()+127)/128,1,1);
        glMemoryBarrier(GL_COMMAND_BARRIER_BIT|GL_SHADER_STORAGE_BARRIER_BIT|GL_UNIFORM_BARRIER_BIT);
        program.run();glDisable(GL_CULL_FACE);glEnable(GL_DEPTH_TEST);glDepthMask(true);
        glColorMask(false,false,false,false);
        glMultiDrawElementsIndirectCount(GL_TRIANGLES,GL_UNSIGNED_INT,0,0,geometry.getSectionCount()*7,0);
        glColorMask(true,true,true,true);
    }

    @Override
    public void shutdown() {
        super.shutdown();
        this.commandGen.free();
        if(shadowCommands!=null)shadowCommands.free();
        this.lodShader.free();
        this.cullShader.free();
        this.glCommandBuffer.free();
        this.glCommandCountBuffer.free();
    }
}
