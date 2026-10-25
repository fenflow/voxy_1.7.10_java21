package me.cortex.voxy.legacy;
import java.util.*;
import me.cortex.voxy.common.util.LatestTaskQueue;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LatestTaskQueueTest {
    @Test void olderBuildFinishingLastCannotOverwriteNewTerrain()throws Exception{
        var queue=new LatestTaskQueue<String>();var output=new ArrayList<String>();
        queue.enqueue(1,"old");var old=queue.take();queue.enqueue(1,"new");var fresh=queue.take();
        assertTrue(queue.deliver(fresh,()->output.add(fresh.value())));
        assertFalse(queue.deliver(old,()->output.add(old.value())));
        assertEquals(List.of("new"),output);assertEquals(0,queue.size());
    }
    @Test void queuedUpdatesCoalesceAndCancelledRetriesStayCancelled()throws Exception{
        var queue=new LatestTaskQueue<String>();queue.enqueue(1,"old");queue.enqueue(1,"new");
        var task=queue.take();assertEquals("new",task.value());queue.cancel(1);queue.retry(task);
        assertEquals(0,queue.size());queue.close();assertNull(queue.take());
    }
    @Test void retryWaitsForModelWithoutDroppingWork()throws Exception{
        var queue=new LatestTaskQueue<String>();queue.enqueue(1,"terrain");var task=queue.take();
        queue.retry(task);assertSame(task,queue.take());assertTrue(queue.deliver(task,()->{}));
    }
    @Test void equalNewWorkEnqueuedDuringDeliveryIsNotLost()throws Exception{
        var queue=new LatestTaskQueue<String>();queue.enqueue(1,"same");var first=queue.take();
        assertTrue(queue.deliver(first,()->queue.enqueue(1,"same")));
        assertEquals(1,queue.size());var second=queue.take();assertTrue(queue.deliver(second,()->{}));
    }
}
