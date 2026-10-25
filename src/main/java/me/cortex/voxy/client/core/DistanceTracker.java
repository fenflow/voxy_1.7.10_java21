package me.cortex.voxy.client.core;

import me.cortex.voxy.client.core.rendering.RenderTracker;

/** Recomputes a complete leaf partition when the camera crosses a 32-block boundary. */
public final class DistanceTracker {
    private final RenderTracker tracker;
    private final int distance,quality,minY,maxY;
    private int x=Integer.MIN_VALUE,z=Integer.MIN_VALUE;
    public DistanceTracker(RenderTracker tracker,int[] scales,int distance,int minY,int maxY){
        this.tracker=tracker;this.distance=distance*32;this.quality=scales[0];this.minY=minY;this.maxY=maxY;
    }
    public void init(int x,int z){setCenter(x,0,z);}
    public void setCenter(int x,int y,int z){
        int sx=x>>5,sz=z>>5;
        if(this.x==sx&&this.z==sz)return;
        this.x=sx;this.z=sz;
        tracker.replaceSelection(LodSelection.select((sx<<5)+16,(sz<<5)+16,distance,quality,minY,maxY));
    }
}
