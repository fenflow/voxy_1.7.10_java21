package me.cortex.voxy.legacy;

import java.lang.reflect.*;
import java.util.*;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.world.biome.BiomeGenBase;

/** Uses GTNHLib's scoped tessellator capture; never renders a tile entity or accesses the world. */
public final class LegacyModelCapture {
    private static Method start,stop,x,y,z,u,v,clear;
    private static boolean unavailable;
    private static void initialize() throws ReflectiveOperationException {
        if(start!=null)return;
        Class<?> manager=Class.forName("com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager");
        start=manager.getMethod("startCapturingAndGet");stop=manager.getMethod("stopCapturingToPooledQuads");
        clear=Class.forName("com.gtnewhorizon.gtnhlib.client.renderer.CapturingTessellator").getMethod("clearQuads");
        Class<?> quad=Class.forName("com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.ModelQuadView");
        x=quad.getMethod("getX",int.class);y=quad.getMethod("getY",int.class);z=quad.getMethod("getZ",int.class);
        u=quad.getMethod("getTexU",int.class);v=quad.getMethod("getTexV",int.class);
    }
    public static List<ModelRasterizer.Vertex[]> capture(LegacyBlockState state){
        // Mod ISBRHs can issue arbitrary GL calls or require live tile entities.
        // Angelica/MCPatcher panes need live neighbour/CTM state; use the icon fallback.
        int type=state.block().getRenderType();
        if(unavailable||state.isAir()||type<0||type>39||type==18||state.block().hasTileEntity(state.metadata()))return null;
        try{initialize();}catch(ReflectiveOperationException e){unavailable=true;return null;}
        var block=state.block();
        double minX=block.getBlockBoundsMinX(),minY=block.getBlockBoundsMinY(),minZ=block.getBlockBoundsMinZ();
        double maxX=block.getBlockBoundsMaxX(),maxY=block.getBlockBoundsMaxY(),maxZ=block.getBlockBoundsMaxZ();
        Tessellator tess=null;boolean capturing=false;
        try{
            var access=new LegacyBlockAccess(state,BiomeGenBase.plains);
            var renderer=new RenderBlocks(access);renderer.renderAllFaces=true;renderer.enableAO=false;
            tess=(Tessellator)start.invoke(null);capturing=true;
            tess.startDrawingQuads();tess.setBrightness(0xF000F0);tess.setColorOpaque_F(1,1,1);
            renderer.renderBlockByRenderType(block,0,0,0);
            List<?> quads=(List<?>)stop.invoke(null);capturing=false;
            var result=new ArrayList<ModelRasterizer.Vertex[]>(quads.size());
            for(Object quad:quads){
                var vertices=new ModelRasterizer.Vertex[4];
                for(int i=0;i<4;i++)vertices[i]=new ModelRasterizer.Vertex((float)x.invoke(quad,i),(float)y.invoke(quad,i),(float)z.invoke(quad,i),(float)u.invoke(quad,i),(float)v.invoke(quad,i));
                result.add(vertices);
            }
            return result.isEmpty()?null:result;
        }catch(ReflectiveOperationException|RuntimeException e){return null;}
        finally{
            if(capturing)try{stop.invoke(null);}catch(ReflectiveOperationException e){ForgeVoxy.LOG.warn("Failed to end model capture",e);}
            if(tess!=null)try{clear.invoke(tess);}catch(ReflectiveOperationException e){ForgeVoxy.LOG.warn("Failed to release captured quads",e);}
            block.setBlockBounds((float)minX,(float)minY,(float)minZ,(float)maxX,(float)maxY,(float)maxZ);
        }
    }
}
