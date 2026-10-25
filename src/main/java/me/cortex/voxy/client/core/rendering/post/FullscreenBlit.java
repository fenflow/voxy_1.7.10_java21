package me.cortex.voxy.client.core.rendering.post;

import me.cortex.voxy.legacy.NativeGL;
import static me.cortex.voxy.legacy.NativeGL.*;

import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gl.shader.ShaderType;


public class FullscreenBlit {
    private static final int EMPTY_VAO = glCreateVertexArrays();

    private final Shader shader;
    public FullscreenBlit(String fragId) {
        this.shader = Shader.make()
                .add(ShaderType.VERTEX, "voxy:post/fullscreen.vert")
                .add(ShaderType.FRAGMENT, fragId)
                .compile();
    }

    public void bind() {
        this.shader.bind();
    }

    public void blit() {
        glBindVertexArray(EMPTY_VAO);
        this.shader.bind();
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);
    }

    public void delete() {
        this.shader.free();
    }
}
