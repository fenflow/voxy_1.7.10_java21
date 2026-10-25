package me.cortex.voxy.legacy;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.util.ForgeDirection;

/** Isolated model/color query context: does not create or tick tile entities. */
public final class LegacyBlockAccess implements IBlockAccess {
    private final LegacyBlockState state;
    private final BiomeGenBase biome;
    public LegacyBlockAccess(LegacyBlockState state, BiomeGenBase biome) { this.state = state; this.biome = biome == null ? BiomeGenBase.plains : biome; }
    public Block getBlock(int x, int y, int z) { return x == 0 && y == 0 && z == 0 ? state.block() : Blocks.air; }
    public TileEntity getTileEntity(int x, int y, int z) { return null; }
    public int getLightBrightnessForSkyBlocks(int x, int y, int z, int minimum) { return 0xF000F0; }
    public int getBlockMetadata(int x, int y, int z) { return state.metadata(); }
    public boolean isAirBlock(int x, int y, int z) { return getBlock(x,y,z) == Blocks.air; }
    public BiomeGenBase getBiomeGenForCoords(int x, int z) { return biome; }
    public int getHeight() { return 256; }
    public boolean extendedLevelsInChunkCache() { return false; }
    public int isBlockProvidingPowerTo(int x, int y, int z, int side) { return 0; }
    public boolean isSideSolid(int x, int y, int z, ForgeDirection side, boolean fallback) { return getBlock(x,y,z).isOpaqueCube(); }
}
