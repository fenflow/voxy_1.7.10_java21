package me.cortex.voxy.legacy;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lwjgl.system.MemoryUtil;
import static org.junit.jupiter.api.Assertions.*;

class CacheTest {
    @TempDir Path directory;
    @Test void persistsAndReplacesOnlyOwnCache(){
        try(var handle=new CacheHandle(directory)){
            handle.cache.setSectionData(-123,ByteBuffer.wrap(new byte[]{1,2,3}));
            handle.cache.setSectionData(-123,ByteBuffer.wrap(new byte[]{4,5}));
            handle.cache.putIdMapping(1,ByteBuffer.wrap(new byte[]{42}));
        }
        try(var handle=new CacheHandle(directory)){
            var data=handle.cache.getSectionData(-123);
            try{assertEquals(2,data.remaining());assertEquals(4,data.get());assertEquals(5,data.get());}
            finally{MemoryUtil.memFree(data);}
            assertArrayEquals(new byte[]{42},handle.cache.getIdMappingsData().get(1));
            assertNull(handle.cache.getSectionData(999));
        }
    }
    @Test void rejectsConcurrentWriters(){try(var handle=new CacheHandle(directory)){assertThrows(Exception.class,()->new FileStorageBackend(directory));}}
    private static class CacheHandle implements AutoCloseable{
        final FileStorageBackend cache;
        CacheHandle(Path path){try{cache=new FileStorageBackend(path);}catch(Exception e){throw new RuntimeException(e);}}
        public void close(){cache.close();}
    }
}
