package me.cortex.voxy.legacy;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModelRasterizerTest {
    private ModelRasterizer.Vertex vertex(float x,float y,float z){return new ModelRasterizer.Vertex(x,y,z,.5f,.5f);}
    @Test void slabTopHasHalfBlockDepth(){
        var quad=new ModelRasterizer.Vertex[]{vertex(0,.5f,0),vertex(1,.5f,0),vertex(1,.5f,1),vertex(0,.5f,1)};
        var face=ModelRasterizer.bake(Collections.singletonList(quad),1,8,new int[]{-1},1,1,false);
        assertEquals(32,(face.data()>>>16)&63);
        assertEquals(0xF0F0,face.data()&65535);
        assertEquals(0,face.metadata()&1); // inset faces cannot hide an adjacent full cube
        assertTrue(Arrays.stream(face.pixels()).allMatch(pixel->pixel==-1));
    }
    @Test void transparentGeometryDoesNotOccludeRearGeometry(){
        var quad=new ModelRasterizer.Vertex[]{vertex(0,0,0),vertex(1,0,0),vertex(1,1,0),vertex(0,1,0)};
        var face=ModelRasterizer.bake(Collections.singletonList(quad),2,8,new int[]{0},1,1,false);
        assertEquals(255,face.metadata());assertEquals(-1,face.data());
    }
    @Test void cubeBoundaryOccludesAndCoversWholeFace(){
        var quad=new ModelRasterizer.Vertex[]{vertex(0,0,0),vertex(1,0,0),vertex(1,1,0),vertex(0,1,0)};
        var face=ModelRasterizer.bake(Collections.singletonList(quad),2,8,new int[]{-1},1,1,false);
        assertEquals(7,face.metadata());assertEquals(0xF0F0,face.data());
    }
}
