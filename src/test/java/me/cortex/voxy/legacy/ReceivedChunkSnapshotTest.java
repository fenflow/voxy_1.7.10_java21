package me.cortex.voxy.legacy;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReceivedChunkSnapshotTest {
    public static final class HodgepodgeMap {
        final List<Object> values = new ArrayList<>();
        public Iterator<Object> valuesIterator() { return values.iterator(); }
    }
    @Test void vanillaSnapshotDoesNotDependOnCameraOrMapApi() {
        var chunks = new ArrayList<>(List.of("far", "near"));
        var snapshot = ReceivedChunkSnapshot.copy(chunks, new Object());
        chunks.clear();
        assertEquals(List.of("far", "near"), snapshot);
    }
    @Test void removedListingUsesHodgepodgeMapIncludingDistantChunks() {
        var map = new HodgepodgeMap();
        map.values.addAll(List.of("outside render distance", "departing"));
        var snapshot = ReceivedChunkSnapshot.copy(null, map);
        map.values.clear();
        assertEquals(List.of("outside render distance", "departing"), snapshot);
    }
}
