package me.cortex.voxy.legacy;

/**
 * Qualifies inherited LWJGL calls with a mod-owned class. Java records NativeGL as
 * the invocation owner, so Angelica's GL-prefix redirector leaves these calls alone.
 * No reflection, transformed-mod exclusions, or duplicated LWJGL classes are needed.
 * Keep Lwjgl3ify-Aware=true in the jar manifest as well.
 */
public final class NativeGL extends org.lwjgl.opengl.GL46C {
    private NativeGL() { }
    public static final int GL_MESH_SHADER_NV = org.lwjgl.opengl.NVMeshShader.GL_MESH_SHADER_NV;
    public static final int GL_TASK_SHADER_NV = org.lwjgl.opengl.NVMeshShader.GL_TASK_SHADER_NV;
    public static final int GL_PARAMETER_BUFFER_ARB = GL_PARAMETER_BUFFER;
    public static final int GL_PARAMETER_BUFFER_BINDING_ARB = GL_PARAMETER_BUFFER_BINDING;
}
