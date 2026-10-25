package me.cortex.voxy.legacy;

import me.cortex.voxy.legacy.NativeGL;
import static me.cortex.voxy.legacy.NativeGL.*;


/** Exact restoration for direct GL calls; Angelica's cached state is never changed. */
public final class GlStateFrame implements AutoCloseable {
    private static final int[] CAPS = {GL_DEPTH_TEST, GL_STENCIL_TEST, GL_BLEND, GL_CULL_FACE, GL_SCISSOR_TEST, GL_FRAMEBUFFER_SRGB, GL_POLYGON_OFFSET_FILL, GL_RASTERIZER_DISCARD, GL_DEPTH_CLAMP};
    private static final int[] PIXEL = {GL_PACK_ALIGNMENT,GL_PACK_ROW_LENGTH,GL_PACK_SKIP_ROWS,GL_PACK_SKIP_PIXELS,
        GL_UNPACK_ALIGNMENT,GL_UNPACK_ROW_LENGTH,GL_UNPACK_SKIP_ROWS,GL_UNPACK_SKIP_PIXELS};
    private final boolean[] enabled = new boolean[CAPS.length];
    private final int[] pixel = new int[PIXEL.length], viewport = new int[4];
    private final int program=glGetInteger(GL_CURRENT_PROGRAM), vao=glGetInteger(GL_VERTEX_ARRAY_BINDING),
        array=glGetInteger(GL_ARRAY_BUFFER_BINDING), draw=glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING), read=glGetInteger(GL_READ_FRAMEBUFFER_BINDING),
        indirect=glGetInteger(GL_DRAW_INDIRECT_BUFFER_BINDING), parameter=glGetInteger(GL_PARAMETER_BUFFER_BINDING_ARB),
        pack=glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING), unpack=glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING),
        active=glGetInteger(GL_ACTIVE_TEXTURE), ssbo=glGetInteger(GL_SHADER_STORAGE_BUFFER_BINDING), ubo=glGetInteger(GL_UNIFORM_BUFFER_BINDING);
    private final int frontFace=glGetInteger(GL_FRONT_FACE),cullFace=glGetInteger(GL_CULL_FACE_MODE),equationRGB=glGetInteger(GL_BLEND_EQUATION_RGB),equationA=glGetInteger(GL_BLEND_EQUATION_ALPHA);
    private final double[] depthRange=new double[2];
    private final int depthFunc=glGetInteger(GL_DEPTH_FUNC);
    private final boolean depthMask=glGetBoolean(GL_DEPTH_WRITEMASK);
    private final int srcRGB=glGetInteger(GL_BLEND_SRC_RGB), dstRGB=glGetInteger(GL_BLEND_DST_RGB), srcA=glGetInteger(GL_BLEND_SRC_ALPHA), dstA=glGetInteger(GL_BLEND_DST_ALPHA);
    private final int[] texture=new int[3], sampler=new int[3], storage=new int[9];
    private final long[] start=new long[9], size=new long[9];
    private final int uniform=glGetIntegeri(GL_UNIFORM_BUFFER_BINDING,0);
    private final long uniformStart=glGetInteger64i(GL_UNIFORM_BUFFER_START,0), uniformSize=glGetInteger64i(GL_UNIFORM_BUFFER_SIZE,0);
    private final int[] front=stencil(false), back=stencil(true);
    private final float[] clear=new float[4];
    private final java.nio.ByteBuffer mask=org.lwjgl.BufferUtils.createByteBuffer(4);
    private final double clearDepth=glGetDouble(GL_DEPTH_CLEAR_VALUE);
    private final int clearStencil=glGetInteger(GL_STENCIL_CLEAR_VALUE);
    private final int[] image={glGetIntegeri(GL_IMAGE_BINDING_NAME,0), glGetIntegeri(GL_IMAGE_BINDING_LEVEL,0),glGetIntegeri(GL_IMAGE_BINDING_LAYERED,0),
        glGetIntegeri(GL_IMAGE_BINDING_LAYER,0),glGetIntegeri(GL_IMAGE_BINDING_ACCESS,0),glGetIntegeri(GL_IMAGE_BINDING_FORMAT,0)};
    private static int[] stencil(boolean back) {
        int[] queries=back?new int[]{GL_STENCIL_BACK_FUNC,GL_STENCIL_BACK_REF,GL_STENCIL_BACK_VALUE_MASK,GL_STENCIL_BACK_WRITEMASK,GL_STENCIL_BACK_FAIL,GL_STENCIL_BACK_PASS_DEPTH_FAIL,GL_STENCIL_BACK_PASS_DEPTH_PASS}
            :new int[]{GL_STENCIL_FUNC,GL_STENCIL_REF,GL_STENCIL_VALUE_MASK,GL_STENCIL_WRITEMASK,GL_STENCIL_FAIL,GL_STENCIL_PASS_DEPTH_FAIL,GL_STENCIL_PASS_DEPTH_PASS};
        return java.util.Arrays.stream(queries).map(q->glGetInteger(q)).toArray();
    }
    public GlStateFrame() {
        glGetDoublev(GL_DEPTH_RANGE,depthRange);
        glGetIntegerv(GL_VIEWPORT,viewport); glGetFloatv(GL_COLOR_CLEAR_VALUE,clear); glGetBooleanv(GL_COLOR_WRITEMASK,mask);
        for(int i=0;i<CAPS.length;i++)enabled[i]=glIsEnabled(CAPS[i]);
        for(int i=0;i<PIXEL.length;i++)pixel[i]=glGetInteger(PIXEL[i]);
        for(int i=0;i<3;i++){glActiveTexture(GL_TEXTURE0+i);texture[i]=glGetInteger(GL_TEXTURE_BINDING_2D);sampler[i]=glGetIntegeri(GL_SAMPLER_BINDING,i);}
        for(int i=0;i<9;i++){storage[i]=glGetIntegeri(GL_SHADER_STORAGE_BUFFER_BINDING,i);start[i]=glGetInteger64i(GL_SHADER_STORAGE_BUFFER_START,i);size[i]=glGetInteger64i(GL_SHADER_STORAGE_BUFFER_SIZE,i);}
        glActiveTexture(GL_TEXTURE0); glBindBuffer(GL_PIXEL_PACK_BUFFER,0);glBindBuffer(GL_PIXEL_UNPACK_BUFFER,0);
        for(int q:PIXEL)glPixelStorei(q,q==GL_PACK_ALIGNMENT||q==GL_UNPACK_ALIGNMENT?4:0);
        for(int i=0;i<3;i++)glBindSampler(i,0);
        glFrontFace(GL_CCW);glCullFace(GL_BACK);glDepthRange(0,1);glBlendEquationSeparate(GL_FUNC_ADD,GL_FUNC_ADD);
        glDisable(GL_POLYGON_OFFSET_FILL);glDisable(GL_RASTERIZER_DISCARD);glDisable(GL_DEPTH_CLAMP);
        glDisable(GL_SCISSOR_TEST);glDisable(GL_BLEND);glDisable(GL_FRAMEBUFFER_SRGB);
        glEnable(GL_DEPTH_TEST);glDepthFunc(GL_LEQUAL);glDepthMask(true);glColorMask(true,true,true,true);
        glStencilMask(255);glClearColor(0,0,0,0);glClearDepth(1);glClearStencil(0);
    }
    public void close() {
        glUseProgram(program);glBindVertexArray(vao);glBindBuffer(GL_ARRAY_BUFFER,array);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER,draw);glBindFramebuffer(GL_READ_FRAMEBUFFER,read);
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER,indirect);glBindBuffer(GL_PARAMETER_BUFFER_ARB,parameter);
        for(int i=0;i<9;i++)restoreRange(GL_SHADER_STORAGE_BUFFER,i,storage[i],start[i],size[i]);
        restoreRange(GL_UNIFORM_BUFFER,0,uniform,uniformStart,uniformSize);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER,ssbo);glBindBuffer(GL_UNIFORM_BUFFER,ubo);
        for(int i=0;i<3;i++){glActiveTexture(GL_TEXTURE0+i);glBindTexture(GL_TEXTURE_2D,texture[i]);glBindSampler(i,sampler[i]);}
        glActiveTexture(active);
        glBindImageTexture(0,image[0],image[1],image[2]!=0,image[3],image[4],image[5]);
        glBindBuffer(GL_PIXEL_PACK_BUFFER,pack);glBindBuffer(GL_PIXEL_UNPACK_BUFFER,unpack);
        for(int i=0;i<PIXEL.length;i++)glPixelStorei(PIXEL[i],pixel[i]);
        for(int i=0;i<CAPS.length;i++){if(enabled[i])glEnable(CAPS[i]);else glDisable(CAPS[i]);}
        glFrontFace(frontFace);glCullFace(cullFace);glDepthRange(depthRange[0],depthRange[1]);glBlendEquationSeparate(equationRGB,equationA);
        glDepthFunc(depthFunc);glDepthMask(depthMask);glColorMask(mask.get(0)!=0,mask.get(1)!=0,mask.get(2)!=0,mask.get(3)!=0);
        glBlendFuncSeparate(srcRGB,dstRGB,srcA,dstA);restoreStencil(GL_FRONT,front);restoreStencil(GL_BACK,back);
        glClearColor(clear[0],clear[1],clear[2],clear[3]);glClearDepth(clearDepth);glClearStencil(clearStencil);
        glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);
    }
    private static void restoreRange(int target,int index,int buffer,long start,long size){
        if(buffer!=0&&size>0)glBindBufferRange(target,index,buffer,start,size);else glBindBufferBase(target,index,buffer);
    }
    private static void restoreStencil(int face,int[] s){glStencilFuncSeparate(face,s[0],s[1],s[2]);glStencilMaskSeparate(face,s[3]);glStencilOpSeparate(face,s[4],s[5],s[6]);}
}
