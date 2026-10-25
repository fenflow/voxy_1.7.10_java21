package me.cortex.voxy.client.core;

import me.cortex.voxy.legacy.NativeGL;
import static me.cortex.voxy.legacy.NativeGL.*;

import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gl.shader.ShaderType;
import org.lwjgl.opengl.GL;

public class Capabilities {

    public static final Capabilities INSTANCE = new Capabilities();

    public final boolean meshShaders;
    public final boolean INT64_t;
    public Capabilities() {
        var cap = GL.getCapabilities();
        this.meshShaders = cap.GL_NV_mesh_shader && cap.GL_NV_representative_fragment_test;
        //this.INT64_t = cap.GL_ARB_gpu_shader_int64 || cap.GL_AMD_gpu_shader_int64;
        //The only reliable way to test for int64 support is to try compile a shader
        this.INT64_t = testShaderCompilesOk(ShaderType.COMPUTE, """
                #version 430
                #extension GL_ARB_gpu_shader_int64 : require
                layout(local_size_x=32) in;
                void main() {
                    uint64_t a = 1234;
                }
                """);
    }

    public static void init() {
    }

    private static boolean testShaderCompilesOk(ShaderType type, String src) {
        int shader = NativeGL.glCreateShader(type.gl);
        NativeGL.glShaderSource(shader, src);
        NativeGL.glCompileShader(shader);
        int result = NativeGL.glGetShaderi(shader, NativeGL.GL_COMPILE_STATUS);
        NativeGL.glDeleteShader(shader);

        return result == NativeGL.GL_TRUE;
    }
}
