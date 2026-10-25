package me.cortex.voxy.legacy;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

/** A registry block plus metadata; GTNH EndlessIDs extends metadata to 16 bits. */
public record LegacyBlockState(Block block, int metadata) {
    public LegacyBlockState {
        if (block == null || metadata < 0 || metadata > 65535) throw new IllegalArgumentException("Invalid block state: " + block + " metadata=" + metadata);
    }
    public boolean isAir() { return block == Blocks.air; }
    public String registryName() { return (String) Block.blockRegistry.getNameForObject(block); }
}
