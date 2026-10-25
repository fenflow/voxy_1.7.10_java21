package me.cortex.voxy.common.world;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class SectionCodecTest {
    private WorldSection section(){return new WorldSection(2,-123,5,345,null);}
    @Test void roundTripAllUniqueVoxels(){
        var source=section();for(int i=0;i<32768;i++)source.data[i]=i*0x123456789L;
        var encoded=SaveLoadSystem.serialize(source);
        try{var decoded=section();assertTrue(SaveLoadSystem.deserialize(decoded,encoded,false));assertArrayEquals(source.data,decoded.data);}
        finally{MemoryUtil.memFree(encoded);}
    }
    @Test void corruptInputCannotChangeDestination(){
        var source=section();Arrays.fill(source.data,123456789L);
        var encoded=SaveLoadSystem.serialize(source);
        try{
            var destination=section();Arrays.fill(destination.data,99L);
            encoded.putLong(encoded.limit()-8,0L);
            assertFalse(SaveLoadSystem.deserialize(destination,encoded,false));
            assertTrue(Arrays.stream(destination.data).allMatch(v->v==99));
        }finally{MemoryUtil.memFree(encoded);}
    }
    @Test void rejectsTruncationOversizePaletteAndWrongPosition(){
        var encoded=SaveLoadSystem.serialize(section());
        try{
            assertFalse(SaveLoadSystem.deserialize(section(),encoded.duplicate().limit(5),false));
            encoded.putInt(8,Integer.MAX_VALUE);assertFalse(SaveLoadSystem.deserialize(section(),encoded,false));
        }finally{MemoryUtil.memFree(encoded);}
        encoded=SaveLoadSystem.serialize(section());
        try{assertFalse(SaveLoadSystem.deserialize(new WorldSection(0,0,0,0,null),encoded,false));}
        finally{MemoryUtil.memFree(encoded);}
    }
    @Test void negativeSectionCoordinatesRoundTrip(){
        for(int level=0;level<=4;level++)for(int x:new int[]{-8388608,-1001,-1,0,1,8388607})
            for(int z:new int[]{-12345,-1,0,12345})for(int y:new int[]{-128,-1,0,127}){
                long key=WorldEngine.getWorldSectionId(level,x,y,z);
                assertEquals(level,WorldEngine.getLevel(key));assertEquals(x,WorldEngine.getX(key));
                assertEquals(y,WorldEngine.getY(key));assertEquals(z,WorldEngine.getZ(key));
            }
    }
}
