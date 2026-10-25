package me.cortex.voxy.common.voxelization;

import me.cortex.voxy.common.world.other.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MipTest {
    @Test void airLightDoesNotOverflowBlockNibble(){
        long value=Mapper.withLight(0,0xA7);
        assertEquals(value,Mipper.mip(value,value,value,value,value,value,value,value,null));
    }
    @Test void materialSurvivesAllFourLevels(){
        var section=VoxelizedSection.createEmpty(-2,4,9);
        long value=Mapper.compose(123,511,0xE3);
        assertEquals(511,Mapper.getBiomeId(value));
        section.section[(15<<8)|(15<<4)|15]=value;
        WorldConversionFactory.mipSection(section,null);
        for(int level=1;level<=4;level++)assertEquals(value,section.get(level,(16>>level)-1,(16>>level)-1,(16>>level)-1));
    }
    @Test void airRemainsAir(){
        var section=VoxelizedSection.createEmpty(0,0,0);
        WorldConversionFactory.mipSection(section,null);
        assertEquals(0,section.get(4,0,0,0));
    }
}
