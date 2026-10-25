package me.cortex.voxy.legacy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DepartureCaptureTest {
    @Test void firstVisitDeparturePrecedesArrivalBacklogAndRetainsFinalData() {
        var queue = new ChunkCaptureQueue<String>();
        for (int i=0;i<289;i++) queue.request(i,"arrival",0xffff);
        queue.departing(280,"final packet data");
        assertFalse(queue.captureOne((c,y)->false));
        for(int i=0;i<16;i++) {
            final int section=i;
            assertTrue(queue.captureOne((c,y)->{
                assertEquals("final packet data",c); assertEquals(section,y);return true;
            }));
        }
        assertEquals(288*16,queue.sectionCount());
    }
    @Test void departureRecapturesSectionsAlreadyAcceptedBeforeFinalPacket() {
        var queue=new ChunkCaptureQueue<String>();queue.request(1,"early",0xffff);
        while(queue.captureOne((c,y)->true)){}
        queue.departing(1,"complete");
        for(int i=0;i<16;i++)assertTrue(queue.captureOne((c,y)->{assertEquals("complete",c);return true;}));
        assertTrue(queue.isEmpty());
    }
    @Test void packetBurstGetsBoundedCatchUpBudget() {
        assertEquals(4,CaptureBudget.sectionLimit(4,0));
        assertEquals(64,CaptureBudget.sectionLimit(4,4624));
        assertEquals(8_000_000L,CaptureBudget.nanoseconds(4624));
        assertEquals(3_000_000L,CaptureBudget.nanoseconds(0));
    }
}
