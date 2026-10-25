package me.cortex.voxy.client.core.gl;

import me.cortex.voxy.legacy.NativeGL;
import static me.cortex.voxy.legacy.NativeGL.*;

import me.cortex.voxy.common.util.TrackedObject;


public class GlBuffer extends TrackedObject {
    public final int id;
    private final long size;

    public GlBuffer(long size) {
        this(size, 0);
    }

    public GlBuffer(long size, int flags) {
        this.id = glCreateBuffers();
        this.size = size;
        glNamedBufferStorage(this.id, size, flags);
    }

    @Override
    public void free() {
        this.free0();
        glDeleteBuffers(this.id);
    }

    public long size() {
        return this.size;
    }
}
