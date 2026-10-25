package me.cortex.voxy.client.core.rendering;

import me.cortex.voxy.legacy.NativeGL;
import static me.cortex.voxy.legacy.NativeGL.*;

import me.cortex.voxy.client.core.gl.GlBuffer;


public class Gl46Viewport extends Viewport<Gl46Viewport> {
    GlBuffer visibilityBuffer;
    public Gl46Viewport(Gl46FarWorldRenderer renderer) {
        super(renderer);
        this.visibilityBuffer = new GlBuffer(renderer.maxSections*4L);
        nglClearNamedBufferData(this.visibilityBuffer.id, GL_R8UI, GL_RED_INTEGER, GL_UNSIGNED_BYTE, 0);
    }

    protected void delete0() {
        this.visibilityBuffer.free();
    }
}
