package me.cortex.voxy.common.world;

import it.unimi.dsi.fastutil.longs.Long2ShortOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;


public class SaveLoadSystem {

    //TODO: Cache like long2short and the short and other data to stop allocs
    public static ByteBuffer serialize(WorldSection section) {
        var data = section.copyData();
        var compressed = new short[data.length];
        Long2ShortOpenHashMap LUT = new Long2ShortOpenHashMap(data.length);
        LongArrayList LUTVAL = new LongArrayList();
        for (int i = 0; i < data.length; i++) {
            long block = data[i];
            short mapping = LUT.computeIfAbsent(block, id->{
                LUTVAL.add(id);
                return (short)(LUTVAL.size()-1);
            });
            compressed[i] = mapping;
        }
        long[] lut = LUTVAL.toLongArray();
        ByteBuffer raw = MemoryUtil.memAlloc(compressed.length*2+lut.length*8+512);

        long hash = section.key^(lut.length*1293481298141L);
        raw.putLong(section.key);
        raw.putInt(lut.length);
        for (long id : lut) {
            raw.putLong(id);
            hash *= 1230987149811L;
            hash += 12831;
            hash ^= id;
        }

        for (int i = 0; i < compressed.length; i++) {
            short block = compressed[i];
            raw.putShort(block);
            hash *= 1230987149811L;
            hash += 12831;
            hash ^= (block*1827631L) ^ data[i];
        }

        raw.putLong(hash);

        raw.limit(raw.position());
        raw.rewind();

        return raw;
    }

    public static boolean deserialize(WorldSection section, ByteBuffer input, boolean ignoreMismatchPosition) {
        ByteBuffer data = input.duplicate().order(input.order());
        if (data.remaining() < 20) return false;
        long key = data.getLong(); int lutLen = data.getInt();
        if ((!ignoreMismatchPosition && key != section.key) || lutLen < 1 || lutLen > 32768) return false;
        if (data.remaining() != lutLen * 8 + 32768 * 2 + 8) return false;
        long[] lut = new long[lutLen], decoded = new long[32768];
        long hash = key ^ (lutLen * 1293481298141L);
        for (int i=0;i<lutLen;i++) { lut[i]=data.getLong(); hash=hash*1230987149811L+12831; hash^=lut[i]; }
        for (int i=0;i<decoded.length;i++) {
            int index = Short.toUnsignedInt(data.getShort());
            if (index >= lutLen) return false;
            decoded[i]=lut[index]; hash=hash*1230987149811L+12831; hash^=(index*1827631L)^decoded[i];
        }
        if (data.getLong()!=hash) return false;
        synchronized(section) { System.arraycopy(decoded,0,section.data,0,decoded.length); }
        return true;
    }
}
