package me.cortex.voxy.client.core.util;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;
import static org.junit.jupiter.api.Assertions.*;

class IndexUtilTest {
    @Test void integerIndicesReserveFourBytesPerElement() {
        var buffer=IndexUtil.generateQuadIndicesInt(2);
        try {
            assertEquals(48,buffer.size);
            int[] expected={0,1,2,1,3,2,4,5,6,5,7,6};
            for(int i=0;i<expected.length;i++)assertEquals(expected[i],MemoryUtil.memGetInt(buffer.address+i*4L));
        } finally { buffer.free(); }
    }
}
