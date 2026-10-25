package me.cortex.voxy.legacy;
import java.util.*;
import me.cortex.voxy.client.core.LodSelection;
import me.cortex.voxy.common.world.WorldEngine;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LodSelectionTest {
    @Test void movementAndTeleportsAlwaysProduceSingleCoverage(){
        var random=new Random(1710);
        for(var center:new int[][]{{0,0},{31,32},{-1,-33},{512,-512},{-10000,10000}}){
            var leaves=LodSelection.select(center[0],center[1],2048,4,0,7);
            for(int i=0;i<15000;i++){
                int x=center[0]+random.nextInt(4096)-2048,z=center[1]+random.nextInt(4096)-2048,y=random.nextInt(256);
                if(Math.hypot(x-center[0],z-center[1])>=2048)continue;
                int covers=0;
                for(int level=0;level<=4;level++)if(leaves.contains(WorldEngine.getWorldSectionId(level,x>>(5+level),y>>(5+level),z>>(5+level))))covers++;
                assertEquals(1,covers,"Coverage at "+x+","+y+","+z);
            }
        }
    }
}
