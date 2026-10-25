package me.cortex.voxy.client.core.gl.shader;

import me.cortex.voxy.legacy.NativeGL;
import static me.cortex.voxy.legacy.NativeGL.*;



public enum ShaderType {
    VERTEX(GL_VERTEX_SHADER),
    FRAGMENT(GL_FRAGMENT_SHADER),
    COMPUTE(GL_COMPUTE_SHADER),
    MESH(GL_MESH_SHADER_NV),
    TASK(GL_TASK_SHADER_NV);
    public final int gl;
    ShaderType(int glEnum) {
        gl = glEnum;
    }
}
