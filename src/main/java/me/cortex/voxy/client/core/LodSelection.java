package me.cortex.voxy.client.core;

import java.util.HashSet;
import java.util.Set;
import me.cortex.voxy.common.world.WorldEngine;

/** An octree leaf partition: every selected point has exactly one level of detail. */
public final class LodSelection {
    public static Set<Long> select(int x,int z,int distance,int quality,int minY,int maxY){
        Set<Long> result=new HashSet<>();
        int size=32<<4;
        for(int a=Math.floorDiv(x-distance,size);a<=Math.floorDiv(x+distance,size);a++)
            for(int c=Math.floorDiv(z-distance,size);c<=Math.floorDiv(z+distance,size);c++)
                for(int b=minY>>4;b<=maxY>>4;b++)visit(result,4,a,b,c,x,z,distance,quality,minY,maxY);
        return result;
    }
    private static void visit(Set<Long> out,int level,int x,int y,int z,int cx,int cz,int distance,int quality,int minY,int maxY){
        if((y<<level)>maxY || ((y+1)<<level)<=minY)return;
        int size=32<<level;
        long dx=Math.max(0,Math.max((long)x*size-cx,cx-(long)(x+1)*size));
        long dz=Math.max(0,Math.max((long)z*size-cz,cz-(long)(z+1)*size));
        long d2=dx*dx+dz*dz;
        if(d2>(long)distance*distance)return;
        if(level>0 && d2<=(long)quality*quality*size*size){
            for(int a=0;a<2;a++)for(int b=0;b<2;b++)for(int c=0;c<2;c++)
                visit(out,level-1,x*2+a,y*2+b,z*2+c,cx,cz,distance,quality,minY,maxY);
        }else out.add(WorldEngine.getWorldSectionId(level,x,y,z));
    }
}
