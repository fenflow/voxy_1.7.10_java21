package me.cortex.voxy.legacy;

import java.util.*;

/** Orthographic six-face bake into Voxy's compact, single-depth model format. */
public final class ModelRasterizer {
    public record Vertex(float x,float y,float z,float u,float v) {}
    public record Face(int[] pixels,int data,int metadata) {}

    public static Face bake(List<Vertex[]> quads,int face,int size,int[] atlas,int width,int height,boolean translucent) {
        int[] pixels=new int[size*size];
        float[] depths=new float[pixels.length]; Arrays.fill(depths,Float.POSITIVE_INFINITY);
        for(Vertex[] q:quads){
            triangle(q[0],q[1],q[2],face,size,atlas,width,height,pixels,depths);
            triangle(q[0],q[2],q[3],face,size,atlas,width,height,pixels,depths);
        }
        int minU=size,minV=size,maxU=-1,maxV=-1,written=0; float depth=0;
        for(int y=0;y<size;y++)for(int x=0;x<size;x++){
            int index=y*size+x;
            if((pixels[index]>>>24)<=64)continue;
            minU=Math.min(minU,x);maxU=Math.max(maxU,x);minV=Math.min(minV,y);maxV=Math.max(maxV,y);
            depth+=depths[index];written++;
        }
        if(written==0)return new Face(pixels,-1,255);
        int indent=Math.clamp(Math.round(depth/written*63),0,63);
        boolean full=written==size*size, boundary=indent==0;
        int data=(minU*16/size)|((maxU*16/size+16/size-1)<<4)|(minV*16/size<<8)|((maxV*16/size+16/size-1)<<12)|(indent<<16);
        if(!translucent&&!full)data|=1<<22|1<<23;
        int metadata=(full?2:0)|(boundary?4:0)|(!translucent&&full&&boundary?1:0)|(!boundary||translucent?8:0);
        return new Face(pixels,data,metadata);
    }
    private static void triangle(Vertex a,Vertex b,Vertex c,int face,int size,int[] atlas,int width,int height,int[] pixels,float[] depths){
        int axis=face>>1;
        float au=u(a,axis),av=v(a,axis),bu=u(b,axis),bv=v(b,axis),cu=u(c,axis),cv=v(c,axis);
        float denominator=(bv-cv)*(au-cu)+(cu-bu)*(av-cv);
        if(Math.abs(denominator)<1e-8f)return;
        for(int y=0;y<size;y++)for(int x=0;x<size;x++){
            float pu=(x+.5f)/size,pv=(y+.5f)/size;
            float wa=((bv-cv)*(pu-cu)+(cu-bu)*(pv-cv))/denominator;
            float wb=((cv-av)*(pu-cu)+(au-cu)*(pv-cv))/denominator,wc=1-wa-wb;
            if(wa<-.00001f||wb<-.00001f||wc<-.00001f)continue;
            float d=wa*d(a,axis)+wb*d(b,axis)+wc*d(c,axis); if((face&1)!=0)d=1-d;
            int index=y*size+x; if(d>=depths[index])continue;
            float tu=wa*a.u+wb*b.u+wc*c.u,tv=wa*a.v+wb*b.v+wc*c.v;
            int color=atlas[Math.clamp((int)(tv*height),0,height-1)*width+Math.clamp((int)(tu*width),0,width-1)];
            if((color>>>24)<=64)continue;
            pixels[index]=color;depths[index]=d;
        }
    }
    private static float u(Vertex v,int axis){return axis==2?v.y:v.x;}
    private static float v(Vertex v,int axis){return axis==0?v.z:axis==1?v.y:v.z;}
    private static float d(Vertex v,int axis){return axis==0?v.y:axis==1?v.z:v.x;}
}
