package me.cortex.voxy.common.voxelization;

import me.cortex.voxy.common.world.other.Mapper;
import me.cortex.voxy.common.world.other.Mipper;
import me.cortex.voxy.legacy.LegacyBlockState;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

public final class WorldConversionFactory {
    /** Call on the client thread. All legacy block, biome and light access finishes here. */
    public static VoxelizedSection capture(Chunk chunk, int sy, Mapper mapper) {
        var result = VoxelizedSection.createEmpty(chunk.xPosition, sy, chunk.zPosition);
        ExtendedBlockStorage section = chunk.getBlockStorageArray()[sy];
        if(section==null){
            java.util.Arrays.fill(result.section,0,4096,Mapper.compose(0,0,chunk.worldObj.provider.hasNoSky?15:0));
            return result;
        }
        int[] biomes = new int[256];
        for (int z=0;z<16;z++) for (int x=0;x<16;x++)
            biomes[(z<<4)|x] = mapper.getIdForBiome(chunk.getBiomeGenForWorldCoords(x,z,chunk.worldObj.getWorldChunkManager()));
        net.minecraft.block.Block previousBlock = null;
        int previousMetadata = -1, previousId = 0, previousEmission = 0;
        for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            int block = 0, light = chunk.worldObj.provider.hasNoSky ? 15 : 0;
            if (section != null) {
                var blockType = section.getBlockByExtId(x, y, z);
                if (blockType == null) blockType = net.minecraft.init.Blocks.air;
                int metadata = section.getExtBlockMetadata(x, y, z);
                if (blockType != previousBlock || metadata != previousMetadata) {
                    previousBlock = blockType; previousMetadata = metadata;
                    previousId = mapper.getIdForBlockState(new LegacyBlockState(blockType, metadata));
                    previousEmission = blockType.getLightValue();
                }
                block = previousId;
                int sky = chunk.worldObj.provider.hasNoSky ? 0 : section.getExtSkylightValue(x, y, z);
                int emitted = Math.min(15, Math.max(section.getExtBlocklightValue(x, y, z), previousEmission));
                light = (15 - sky) | (emitted << 4);
            }
            result.section[(y << 8) | (z << 4) | x] = Mapper.compose(block, biomes[(z << 4) | x], light);
        }
        return result;
    }
    /** Same 2x2x2 reduction and layout as upstream Voxy, generalized across its four levels. */
    public static void mipSection(VoxelizedSection section, Mapper mapper) {
        long[] data = section.section;
        int source = 0, target = 4096;
        for (int size = 16; size > 1; size >>= 1) {
            int half = size >> 1, index = target;
            for (int y = 0; y < size; y += 2) for (int z = 0; z < size; z += 2) for (int x = 0; x < size; x += 2) {
                int a = source + (y * size + z) * size + x, dy = size * size;
                data[index++] = Mipper.mip(data[a], data[a+1], data[a+size], data[a+size+1],
                    data[a+dy], data[a+dy+1], data[a+dy+size], data[a+dy+size+1], mapper);
            }
            source = target; target += half * half * half;
        }
    }
}
