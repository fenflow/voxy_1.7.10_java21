package me.cortex.voxy.legacy;

import cpw.mods.fml.relauncher.ReflectionHelper;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;

public final class LegacyLightmap {
    private static final Field COLORS = ReflectionHelper.findField(EntityRenderer.class, "lightmapColors", "field_78504_Q");
    public static int[] read() {
        try { return (int[]) COLORS.get(Minecraft.getMinecraft().entityRenderer); }
        catch (IllegalAccessException e) { throw new IllegalStateException("Cannot read the legacy lightmap", e); }
    }
}
