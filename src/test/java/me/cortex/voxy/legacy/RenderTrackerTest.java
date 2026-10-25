package me.cortex.voxy.legacy;
import java.util.*;
import me.cortex.voxy.client.core.rendering.RenderTracker;
import me.cortex.voxy.client.core.rendering.building.BuiltSection;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RenderTrackerTest {
    @Test void lodReplacementWaitsForEveryChildIncludingEmptyChildren(){
        var batches=new ArrayList<List<BuiltSection>>();var requested=new ArrayList<Long>();
        var tracker=new RenderTracker(requested::add,k->{},batches::add);
        tracker.replaceSelection(Set.of(100L));tracker.processBuildResult(new BuiltSection(100));batches.clear();
        tracker.replaceSelection(Set.of(1L,2L,3L));
        tracker.processBuildResult(new BuiltSection(2));tracker.processBuildResult(new BuiltSection(1));
        assertTrue(batches.isEmpty(),"Old terrain must stay until every replacement is ready");
        tracker.processBuildResult(new BuiltSection(3));assertEquals(1,batches.size());
        assertEquals(4,batches.get(0).size());assertEquals(100,batches.get(0).getLast().position);
    }
    @Test void reversingDirectionDiscardsCancelledTransitionWithoutDeletingFallback(){
        var batches=new ArrayList<List<BuiltSection>>();var tracker=new RenderTracker(k->{},k->{},batches::add);
        tracker.replaceSelection(Set.of(100L));tracker.processBuildResult(new BuiltSection(100));batches.clear();
        tracker.replaceSelection(Set.of(1L,2L));tracker.processBuildResult(new BuiltSection(1));
        tracker.replaceSelection(Set.of(100L));tracker.processBuildResult(new BuiltSection(2));assertTrue(batches.isEmpty());
        tracker.replaceSelection(Set.of());assertEquals(100,batches.getFirst().getFirst().position);
    }
    @Test void inactiveChangedSectionStillInvalidatesItsActiveVerticalNeighbour(){
        var requests=new ArrayList<Long>();var tracker=new RenderTracker(requests::add,k->{},batch->{});
        long above=me.cortex.voxy.common.world.WorldEngine.getWorldSectionId(0,3,2,-4);
        tracker.replaceSelection(Set.of(above));requests.clear();
        var cache=new me.cortex.voxy.common.world.ActiveSectionTracker(1,s->1);
        var changed=cache.acquire(0,3,1,-4,false);
        try{tracker.sectionUpdated(changed);assertEquals(List.of(above),requests);}finally{changed.release();}
    }
}
