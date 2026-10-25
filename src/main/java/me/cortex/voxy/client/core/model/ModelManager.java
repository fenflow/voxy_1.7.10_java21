package me.cortex.voxy.client.core.model;

import me.cortex.voxy.legacy.NativeGL;
import static me.cortex.voxy.legacy.NativeGL.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicIntegerArray;
import me.cortex.voxy.client.core.gl.*;
import me.cortex.voxy.client.core.rendering.util.UploadStream;
import me.cortex.voxy.legacy.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.IIcon;
import net.minecraft.world.biome.BiomeGenBase;
import org.lwjgl.system.MemoryUtil;

/** Legacy model adapter for the unchanged Voxy quad/model GPU format. */
public final class ModelManager {
    public static final int MODEL_SIZE = 64;
    private final GlBuffer modelBuffer = new GlBuffer(65536L * MODEL_SIZE);
    private final GlBuffer colorBuffer = new GlBuffer(65536L * 512 * 4);
    private final GlTexture texture;
    private final int sampler = glGenSamplers();
    private final int size;
    private final long[] metadata = new long[65536];
    private final AtomicIntegerArray ready = new AtomicIntegerArray(65536);
    private final LegacyBlockState[] states = new LegacyBlockState[65536];
    private final BiomeGenBase[] biomes = new BiomeGenBase[512];
    private int[] atlasPixels;
    private int atlasWidth, atlasHeight, count;
    private Object shaderMapping;

    private int shaderId(LegacyBlockState state){
        var map=net.coderbot.iris.block_rendering.BlockRenderingSettings.INSTANCE.getBlockMetaMatches();
        var metas=map==null?null:map.get(state.block());
        return metas==null?-1:net.coderbot.iris.block_rendering.BlockMaterialMapping.resolveId(metas,state.metadata());
    }
    public void refreshShaderIds(){
        Object current=net.coderbot.iris.block_rendering.BlockRenderingSettings.INSTANCE.getBlockMetaMatches();
        if(current==shaderMapping)return;shaderMapping=current;
        for(int i=0;i<states.length;i++)if(states[i]!=null){
            long ptr=UploadStream.INSTANCE.upload(modelBuffer,(long)i*MODEL_SIZE+36,4);
            MemoryUtil.memPutInt(ptr,shaderId(states[i]));
        }
    }

    public ModelManager(int size) {
        this.size = size;
        texture = new GlTexture().store(GL_RGBA8, 1, size * 3 * 256, size * 2 * 256);
        glSamplerParameteri(sampler, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glSamplerParameteri(sampler, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glSamplerParameteri(sampler, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glSamplerParameteri(sampler, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    }
    private void readAtlas() {
        var atlas = Minecraft.getMinecraft().getTextureManager().getTexture(TextureMap.locationBlocksTexture);
        int id = atlas.getGlTextureId();
        atlasWidth = glGetTextureLevelParameteri(id, 0, GL_TEXTURE_WIDTH);
        atlasHeight = glGetTextureLevelParameteri(id, 0, GL_TEXTURE_HEIGHT);
        atlasPixels = new int[Math.multiplyExact(atlasWidth, atlasHeight)];
        glGetTextureImage(id, 0, GL_RGBA, GL_UNSIGNED_BYTE, atlasPixels);
    }
    public int addEntry(int id, LegacyBlockState state) {
        if (ready.get(id) != 0) return id;
        if (atlasPixels == null) readAtlas();
        boolean translucent = state.block().getRenderBlockPass() == 1;
        long meta = ((long)(1 | (translucent ? 2 : 0) | 32)) << 48;
        long ptr = UploadStream.INSTANCE.upload(modelBuffer, (long)id * MODEL_SIZE, MODEL_SIZE);
        MemoryUtil.memSet(ptr, 0, MODEL_SIZE);
        var captured = LegacyModelCapture.capture(state);
        for (int face = 0; face < 6; face++) {
            ModelRasterizer.Face baked = captured == null ? null : ModelRasterizer.bake(captured,face,size,atlasPixels,atlasWidth,atlasHeight,translucent);
            IIcon icon;
            try { icon = state.block().getIcon(face, state.metadata()); }
            catch (RuntimeException e) { icon = null; }
            if (icon == null) icon = Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("missingno");
            int[] pixels = new int[size * size]; int written = 0;
            for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
                int ax = Math.min(atlasWidth-1, Math.max(0, (int)((icon.getMinU() + (icon.getMaxU()-icon.getMinU())*(x+.5)/size)*atlasWidth)));
                int ay = Math.min(atlasHeight-1, Math.max(0, (int)((icon.getMinV() + (icon.getMaxV()-icon.getMinV())*(y+.5)/size)*atlasHeight)));
                int rgba = atlasPixels[ay * atlasWidth + ax];
                pixels[y*size+x] = rgba; if ((rgba >>> 24) > 64) written++;
            }
            if (baked != null) pixels = baked.pixels();
            if (state.isAir()) written = 0;
            int faceBits = written == 0 ? 255 : 2 | 4 | (translucent ? 8 : (written == pixels.length ? 1 : 0));
            if (baked != null) faceBits = baked.metadata();
            meta |= (long)faceBits << (face*8);
            int data = written == 0 ? -1 : 0xF0F0 | ((!translucent && written != pixels.length) ? 1 << 22 : 0);
            if (baked != null) data = baked.data();
            MemoryUtil.memPutInt(ptr + face*4L, data);
            glTextureSubImage2D(texture.id, 0, (id & 255)*size*3 + (face>>1)*size, (id>>>8)*size*2 + (face&1)*size,
                size,size,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
        }
        MemoryUtil.memPutInt(ptr + 24, 3 | (translucent ? 4 : 0) | 8);
        MemoryUtil.memPutInt(ptr + 28, id*512);
        MemoryUtil.memPutInt(ptr + 32, me.cortex.voxy.legacy.shader.ShaderMaterial.id(state));
        MemoryUtil.memPutInt(ptr + 36, shaderId(state));
        long colors = UploadStream.INSTANCE.upload(colorBuffer, id*512L*4, 512*4);
        for (int biome = 0; biome < 512; biome++) {
            int color;
            try { color = state.block().colorMultiplier(new LegacyBlockAccess(state, biomes[biome]),0,0,0); }
            catch (RuntimeException e) { color = state.block().getRenderColor(state.metadata()); }
            MemoryUtil.memPutInt(colors + biome*4L, color | 0xFF000000);
        }
        states[id] = state;
        metadata[id] = meta;
        ready.set(id, 1); // Release publishes metadata to meshing workers.
        count++; return id;
    }
    public void addBiome(int id, BiomeGenBase biome) {
        biomes[id] = biome;
        for (int model = 0; model < states.length; model++) {
            LegacyBlockState state = states[model]; if (state == null) continue;
            int color;
            try { color = state.block().colorMultiplier(new LegacyBlockAccess(state, biome),0,0,0); }
            catch (RuntimeException e) { color = state.block().getRenderColor(state.metadata()); }
            long ptr = UploadStream.INSTANCE.upload(colorBuffer, (model*512L + id)*4, 4);
            MemoryUtil.memPutInt(ptr, color | 0xFF000000);
        }
    }
    public long getModelMetadata(int id) { check(id); return metadata[id]; }
    public long getModelMetadataFromClientId(int id) { return getModelMetadata(id); }
    public int getModelId(int id) { check(id); return id; }
    public int getFluidClientStateId(int id) { check(id); return id; }
    private void check(int id) { if (ready.get(id) == 0) throw new IdNotYetComputedException(id); }
    public int getBufferId() { return modelBuffer.id; }
    public int getColourBufferId() { return colorBuffer.id; }
    public int getTextureId() { return texture.id; }
    public int getSamplerId() { return sampler; }
    public void free() { texture.free(); modelBuffer.free(); colorBuffer.free(); glDeleteSamplers(sampler); atlasPixels=null; }
    public void addDebugInfo(List<String> out) { out.add("Voxy legacy models: " + count); }
    public static boolean faceExists(long m,int f) { return ((m >>> (f*8)) & 255) != 255; }
    public static boolean faceCanBeOccluded(long m,int f) { return ((m >>> (f*8)) & 4) != 0; }
    public static boolean faceOccludes(long m,int f) { return faceExists(m,f) && ((m >>> (f*8)) & 1) != 0; }
    public static boolean faceUsesSelfLighting(long m,int f) { return ((m >>> (f*8)) & 8) != 0; }
    public static boolean isDoubleSided(long m) { return (m & (4L<<48)) != 0; }
    public static boolean isTranslucent(long m) { return (m & (2L<<48)) != 0; }
    public static boolean containsFluid(long m) { return (m & (8L<<48)) != 0; }
    public static boolean isFluid(long m) { return (m & (16L<<48)) != 0; }
    public static boolean isBiomeColoured(long m) { return (m & (1L<<48)) != 0; }
    public static boolean cullsSame(long m) { return (m & (32L<<48)) != 0; }
}
