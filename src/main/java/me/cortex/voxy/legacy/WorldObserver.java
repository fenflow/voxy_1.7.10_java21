package me.cortex.voxy.legacy;

import net.minecraft.world.IWorldAccess;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

final class WorldObserver implements IWorldAccess {
    private final LegacySession session;
    WorldObserver(LegacySession session){this.session=session;}
    public void markBlockForUpdate(int x,int y,int z){session.dirty(x,y,z);}
    public void markBlockForRenderUpdate(int x,int y,int z){session.dirty(x,y,z);}
    public void markBlockRangeForRenderUpdate(int x,int y,int z,int xx,int yy,int zz){
        if((long)xx-x>1024||(long)zz-z>1024)return;
        for(int a=x>>4;a<=xx>>4;a++)for(int b=Math.max(0,y>>4);b<=Math.min(15,yy>>4);b++)for(int c=z>>4;c<=zz>>4;c++)session.dirty(a<<4,b<<4,c<<4);
    }
    public void playSound(String s,double x,double y,double z,float v,float p){}
    public void playSoundToNearExcept(EntityPlayer p,String s,double x,double y,double z,float v,float pitch){}
    public void spawnParticle(String s,double x,double y,double z,double vx,double vy,double vz){}
    public void onEntityCreate(Entity e){}
    public void onEntityDestroy(Entity e){}
    public void playRecord(String s,int x,int y,int z){}
    public void broadcastSound(int id,int x,int y,int z,int data){}
    public void playAuxSFX(EntityPlayer p,int id,int x,int y,int z,int data){}
    public void destroyBlockPartially(int id,int x,int y,int z,int progress){}
    public void onStaticEntitiesChanged(){}
}
