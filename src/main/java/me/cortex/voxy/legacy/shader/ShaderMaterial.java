package me.cortex.voxy.legacy.shader;
import me.cortex.voxy.legacy.LegacyBlockState;
import net.minecraft.block.material.Material;
/** Standard DH material categories consumed by distant-terrain shader programs. */
public final class ShaderMaterial {
    public static int id(LegacyBlockState state) {
        var block=state.block();var m=block.getMaterial();
        if(m==Material.air)return 14;
        if(m==Material.water)return 12;
        if(m==Material.lava)return 6;
        if(block.getLightValue()>0)return 15;
        if(m==Material.leaves)return 1;
        if(m==Material.rock)return 2;
        if(m==Material.wood)return 3;
        if(m==Material.iron)return 4;
        if(m==Material.grass)return 13;
        if(m==Material.ground)return 5;
        if(m==Material.sand)return 9;
        if(m==Material.snow||m==Material.craftedSnow)return 8;
        return 0;
    }
}
