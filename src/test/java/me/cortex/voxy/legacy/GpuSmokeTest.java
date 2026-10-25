package me.cortex.voxy.legacy;

import me.cortex.voxy.client.core.gl.shader.*;
import org.junit.jupiter.api.*;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL45C.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("gpu")
class GpuSmokeTest {
    @Test void compileVoxyPipelinesAndRestoreState(){
        var callback=GLFWErrorCallback.createPrint(System.err);callback.set();
        assertTrue(glfwInit());
        long window=0;
        try{
            glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,4);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,6);
            window=glfwCreateWindow(64,64,"Voxy GPU smoke test",0,0);assertNotEquals(0,window);
            glfwMakeContextCurrent(window);GL.createCapabilities();
            System.out.println("GPU: "+glGetString(GL_RENDERER)+" / "+glGetString(GL_VERSION));
            var command=Shader.make().define("OPAQUE_COMMAND_CAPACITY",2800000).add(ShaderType.COMPUTE,"voxy:lod/gl46/cmdgen.comp").compile();command.free();
            var shadowCommand=Shader.make().define("VOXY_SHADOW").define("OPAQUE_COMMAND_CAPACITY",2800000).add(ShaderType.COMPUTE,"voxy:lod/gl46/cmdgen.comp").compile();shadowCommand.free();
            var opaque=Shader.make().add(ShaderType.VERTEX,"voxy:lod/gl46/quads2.vert").add(ShaderType.FRAGMENT,"voxy:lod/gl46/quads.frag").compile();opaque.free();
            var cull=Shader.make().add(ShaderType.VERTEX,"voxy:lod/gl46/cull/raster.vert").add(ShaderType.FRAGMENT,"voxy:lod/gl46/cull/raster.frag").compile();cull.free();
            var ssao=Shader.make().add(ShaderType.COMPUTE,"voxy:post/ssao.comp").compile();ssao.free();
            glEnable(GL_BLEND);glDepthMask(false);glClearColor(.1f,.2f,.3f,.4f);glViewport(3,4,20,30);
            try(var guard=new GlStateFrame()){glDisable(GL_BLEND);glDepthMask(true);glViewport(0,0,64,64);}
            assertTrue(glIsEnabled(GL_BLEND));assertFalse(glGetBoolean(GL_DEPTH_WRITEMASK));
            int[] view=new int[4];glGetIntegerv(GL_VIEWPORT,view);assertArrayEquals(new int[]{3,4,20,30},view);
            testDepthComposition();
            testIndependentNearDepthMask();
            assertEquals(GL_NO_ERROR,glGetError());
        }finally{if(window!=0)glfwDestroyWindow(window);glfwTerminate();callback.free();}
    }
    private static void testIndependentNearDepthMask(){
        int source=glCreateTextures(GL_TEXTURE_2D),mask=glCreateTextures(GL_TEXTURE_2D);
        glTextureStorage2D(source,1,GL_DEPTH24_STENCIL8,17,19);glTextureStorage2D(mask,1,GL_R32F,17,19);
        glTextureParameteri(source,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTextureParameteri(source,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
        glClearTexImage(source,0,GL_DEPTH_STENCIL,GL_UNSIGNED_INT_24_8,new int[]{0x7fffff00});
        var copy=Shader.make().add(ShaderType.COMPUTE,"voxy:post/copy_depth.comp").compile();
        try{
            copy.bind();glBindTextureUnit(0,source);glBindImageTexture(0,mask,0,false,0,GL_WRITE_ONLY,GL_R32F);
            glDispatchCompute(2,2,1);glMemoryBarrier(GL_TEXTURE_UPDATE_BARRIER_BIT);
            // Changes to the shared world depth must not change the saved ownership mask.
            glClearTexImage(source,0,GL_DEPTH_STENCIL,GL_UNSIGNED_INT_24_8,new int[]{0xffffff00});
            float[] values=new float[17*19];glGetTextureImage(mask,0,GL_RED,GL_FLOAT,values);
            for(float value:values)assertEquals(.5f,value,.0001f);
        }finally{copy.free();glBindImageTexture(0,0,0,false,0,GL_READ_ONLY,GL_R32F);glDeleteTextures(source);glDeleteTextures(mask);}
    }
    private static void testDepthComposition(){
        int source=glCreateFramebuffers(),colour=glCreateTextures(GL_TEXTURE_2D),depth=glCreateTextures(GL_TEXTURE_2D);
        glTextureStorage2D(colour,1,GL_RGBA8,64,64);glTextureStorage2D(depth,1,GL_DEPTH_COMPONENT32F,64,64);
        glTextureParameteri(depth,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTextureParameteri(depth,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
        glNamedFramebufferTexture(source,GL_COLOR_ATTACHMENT0,colour,0);glNamedFramebufferTexture(source,GL_DEPTH_ATTACHMENT,depth,0);
        assertEquals(GL_FRAMEBUFFER_COMPLETE,glCheckNamedFramebufferStatus(source,GL_FRAMEBUFFER));
        var post=new me.cortex.voxy.client.core.rendering.post.PostProcessing();
        var fill=new me.cortex.voxy.client.core.rendering.post.FullscreenBlit("voxy:post/noop.frag");
        try(var state=new GlStateFrame()){
            glBindFramebuffer(GL_FRAMEBUFFER,source);glViewport(0,0,64,64);glClearColor(.2f,.3f,.4f,1);glClearDepth(1);
            glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);glEnable(GL_SCISSOR_TEST);glScissor(0,0,32,64);glClearDepth(.5);
            glClear(GL_DEPTH_BUFFER_BIT);glDisable(GL_SCISSOR_TEST);glClearColor(0,0,0,0);glClearDepth(1);
            post.setup(64,64,source);
            var stencil=org.lwjgl.BufferUtils.createByteBuffer(64*64);glReadPixels(0,0,64,64,GL_STENCIL_INDEX,GL_UNSIGNED_BYTE,stencil);
            assertEquals(0,stencil.get(16+32*64));assertEquals(1,stencil.get(48+32*64));
            fill.blit();post.renderPost(new org.joml.Matrix4f(),new org.joml.Matrix4f(),source);
            var pixels=org.lwjgl.BufferUtils.createByteBuffer(64*64*4);glReadPixels(0,0,64,64,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
            assertEquals(51,Byte.toUnsignedInt(pixels.get((16+32*64)*4)),1,"Foreground must remain shader-rendered");
            assertEquals(255,Byte.toUnsignedInt(pixels.get((48+32*64)*4)),"LOD must fill sky pixels");
            assertEquals(GL_NO_ERROR,glGetError(),"Float shader depth must compose without format mismatch");
        }finally{fill.delete();post.shutdown();glDeleteFramebuffers(source);glDeleteTextures(colour);glDeleteTextures(depth);}
    }
}
