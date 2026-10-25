package me.cortex.voxy.legacy;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ChunkCaptureQueueTest {
    @Test void unloadingAndWorkerBackpressureCannotDropSections(){
        var queue=new ChunkCaptureQueue<Object>();
        var chunk=new Object();queue.request(7,chunk,0xffff);
        assertFalse(queue.captureOne((c,y)->false));assertEquals(16,queue.sectionCount());
        var captured=new HashSet<Integer>();
        while(queue.captureOne((c,y)->{assertSame(chunk,c);assertTrue(captured.add(y));return true;})){}
        assertEquals(16,captured.size());assertTrue(queue.isEmpty());
    }
    @Test void burstBeyondOldIngestCapacitySurvivesUntilDrain(){
        var queue=new ChunkCaptureQueue<Long>();
        for(long c=0;c<600;c++)queue.request(c,c,0xffff);
        var captured=new HashSet<Long>();
        while(queue.captureOne((c,y)->captured.add(c*16+y))){}
        assertEquals(9600,captured.size());assertTrue(queue.isEmpty());
    }
    @Test void updatesCoalesceAndReloadUsesTheLatestChunkObject(){
        var queue=new ChunkCaptureQueue<String>();queue.request(1,"old",3);
        queue.request(1,"new",4);assertEquals(3,queue.sectionCount());
        while(queue.captureOne((c,y)->{assertEquals("new",c);return true;})){}
    }
}
