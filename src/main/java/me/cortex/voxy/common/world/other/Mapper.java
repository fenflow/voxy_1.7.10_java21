package me.cortex.voxy.common.world.other;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.Consumer;
import me.cortex.voxy.common.storage.StorageBackend;
import me.cortex.voxy.legacy.LegacyBlockState;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.BiomeGenBase;

/** Preserves upstream's 64-bit voxel layout; persists registry names, never session block IDs. */
public final class Mapper {
    public static final long AIR = 0, UNKNOWN_MAPPING = -1;
    private final StorageBackend storage;
    private final Map<LegacyBlockState, Integer> ids = new HashMap<>();
    private final List<StateEntry> states = new ArrayList<>();
    private final Map<BiomeGenBase, Integer> biomeIds = new IdentityHashMap<>();
    private final List<BiomeEntry> biomes = new ArrayList<>();
    private Consumer<StateEntry> onState;
    private Consumer<BiomeEntry> onBiome;

    public Mapper(StorageBackend storage) {
        this.storage = storage;
        var air = new LegacyBlockState(Blocks.air, 0);
        states.add(new StateEntry(0, air)); ids.put(air, 0);
        var saved = storage.getIdMappingsData();
        int[] keys = saved.keySet().toIntArray(); Arrays.sort(keys);
        for (int id : keys) {
            if (id < 0) continue;
            if (id != states.size() || id >= 65536) throw new IllegalStateException("Invalid Voxy palette ID " + id);
            try (var in = new DataInputStream(new ByteArrayInputStream(saved.get(id)))) {
                String name = in.readUTF(); int meta = in.readUnsignedShort();
                Block block = Block.getBlockFromName(name);
                var state = new LegacyBlockState(block == null ? Blocks.air : block, meta);
                states.add(new StateEntry(id, state));
                if (!state.isAir()) ids.putIfAbsent(state, id);
            } catch (IOException e) { throw new UncheckedIOException(e); }
        }
        for (int compact = 0; saved.containsKey(-1-compact); compact++) {
            try (var in = new DataInputStream(new ByteArrayInputStream(saved.get(-1-compact)))) {
                int original = in.readInt(); String name = in.readUTF();
                BiomeGenBase match = null;
                for (BiomeGenBase biome : BiomeGenBase.getBiomeGenArray()) {
                    if (biome != null && name.equals(biome.biomeName)) {
                        if (match == null || biome.biomeID == original) match = biome;
                    }
                }
                if (match == null) match = BiomeGenBase.plains;
                if (compact >= 512) throw new IllegalStateException("Invalid biome palette");
                biomes.add(new BiomeEntry(compact, match)); biomeIds.putIfAbsent(match, compact);
            } catch (IOException e) { throw new UncheckedIOException(e); }
        }
    }
    public synchronized int getIdForBiome(BiomeGenBase biome) {
        if (biome == null) biome = BiomeGenBase.plains;
        Integer old = biomeIds.get(biome); if (old != null) return old;
        int id = biomes.size();
        if (id >= 512) throw new IllegalStateException("Voxy's voxel format supports at most 512 encountered biomes");
        try {
            var bytes = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(bytes)) { out.writeInt(biome.biomeID); out.writeUTF(biome.biomeName); }
            storage.putIdMapping(-1-id, ByteBuffer.wrap(bytes.toByteArray()));
        } catch (IOException e) { throw new UncheckedIOException(e); }
        var entry = new BiomeEntry(id, biome); biomes.add(entry); biomeIds.put(biome, id);
        if (onBiome != null) onBiome.accept(entry);
        return id;
    }
    public synchronized int getIdForBlockState(LegacyBlockState state) {
        if (state.isAir()) return 0;
        Integer old = ids.get(state); if (old != null) return old;
        int id = states.size();
        if (id >= 65536) throw new IllegalStateException("Voxy's model format supports at most 65536 states");
        try {
            var bytes = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(bytes)) { out.writeUTF(state.registryName()); out.writeShort(state.metadata()); }
            storage.putIdMapping(id, ByteBuffer.wrap(bytes.toByteArray()));
        } catch (IOException e) { throw new UncheckedIOException(e); }
        var entry = new StateEntry(id, state); states.add(entry); ids.put(state, id);
        if (onState != null) onState.accept(entry);
        return id;
    }
    public synchronized LegacyBlockState getBlockStateFromBlockId(int id) { return states.get(id).state; }
    public synchronized List<StateEntry> getStateEntries() { return List.copyOf(states); }
    public synchronized List<BiomeEntry> getBiomeEntries() { return List.copyOf(biomes); }
    public synchronized void setCallbacks(Consumer<StateEntry> states, Consumer<BiomeEntry> biomes) {
        onState = states; onBiome = biomes;
    }
    public static boolean isAir(long id) { return getBlockId(id) == 0; }
    public static int getBlockId(long id) { return (int) ((id >>> 27) & 0xFFFFF); }
    public static int getBiomeId(long id) { return (int) ((id >>> 47) & 511); }
    public static int getLightId(long id) { return (int) (id >>> 56); }
    public static long withLight(long id, int light) { return (id & 0x00FFFFFFFFFFFFFFL) | ((long)(light & 255) << 56); }
    public static long compose(int block, int biome, int light) {
        return ((long)block << 27) | ((long)(biome & 511) << 47) | ((long)(light & 255) << 56);
    }
    public static final class StateEntry {
        public final int id; public final LegacyBlockState state;
        public StateEntry(int id, LegacyBlockState state) { this.id = id; this.state = state; }
    }
    public static final class BiomeEntry {
        public final int id; public final BiomeGenBase biome;
        public BiomeEntry(int id, BiomeGenBase biome) { this.id = id; this.biome = biome; }
    }
}
