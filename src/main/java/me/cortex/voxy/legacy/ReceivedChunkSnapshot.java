package me.cortex.voxy.legacy;

import java.lang.reflect.Method;
import java.util.*;

/** Snapshot on the client thread, after packet application. Hodgepodge removes chunkListing. */
final class ReceivedChunkSnapshot {
    private static final ClassValue<Method> ITERATORS = new ClassValue<>() {
        protected Method computeValue(Class<?> type) {
            try { return type.getMethod("valuesIterator"); }
            catch (NoSuchMethodException e) { throw new IllegalStateException("Unsupported client chunk map: " + type.getName(), e); }
        }
    };
    static List<?> copy(Collection<?> vanillaList, Object mapping) {
        if (vanillaList != null) return new ArrayList<>(vanillaList);
        try {
            var result = new ArrayList<>();
            ((Iterator<?>) ITERATORS.get(mapping.getClass()).invoke(mapping)).forEachRemaining(result::add);
            return result;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot enumerate Hodgepodge client chunks", e);
        }
    }
}
