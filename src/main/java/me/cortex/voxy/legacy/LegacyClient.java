package me.cortex.voxy.legacy;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.command.*;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkEvent;
import org.lwjgl.opengl.GL;

public final class LegacyClient {
    private static LegacyClient instance;
    private LegacySession session;
    private boolean reload;
    private String failure;
    private boolean shaderPass;
    public static void install(){
        var client=new LegacyClient();
        instance=client;
        MinecraftForge.EVENT_BUS.register(client);FMLCommonHandler.instance().bus().register(client);
        ((IReloadableResourceManager)Minecraft.getMinecraft().getResourceManager()).registerReloadListener(manager->client.reload=true);
        ClientCommandHandler.instance.registerCommand(new CommandBase(){
            public String getCommandName(){return "voxy";}
            public String getCommandUsage(ICommandSender sender){return "/voxy [status|on|off|reload]";}
            public int getRequiredPermissionLevel(){return 0;}
            public void processCommand(ICommandSender sender,String[] args){
                String action=args.length==0?"status":args[0];
                switch(action){
                    case "on" -> {LegacyConfig.enabled=true;client.failure=null;}
                    case "off" -> LegacyConfig.enabled=false;
                    case "reload" -> {client.reload=true;client.failure=null;}
                    case "status" -> {}
                    default -> throw new WrongUsageException(getCommandUsage(sender));
                }
                sender.addChatMessage(new ChatComponentText(client.status()));
                if (action.equals("status") && client.session != null) {
                    var lines = new ArrayList<String>(); client.session.debug(lines);
                    for (String line : lines) sender.addChatMessage(new ChatComponentText(line));
                }
            }
        });
    }
    private String status(){
        if(failure!=null)return "Voxy unavailable: "+failure;
        if(!LegacyConfig.enabled)return "Voxy disabled";
        if(shaderPass)return LegacyConfig.shaderCompatibility?me.cortex.voxy.legacy.shader.ShaderBridge.status():"Voxy LOD paused: shader compatibility is disabled in config";
        return session==null?"Voxy waiting for a world":"Voxy experimental legacy renderer active";
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getMinecraft();
        if(session!=null&&(session.clientWorld!=mc.theWorld||reload||!LegacyConfig.enabled))close();
        reload=false;
        if(session!=null){try{session.tick();}catch(RuntimeException e){fail(e);}}
    }
    @SubscribeEvent public void render(RenderWorldLastEvent event){
        var mc=Minecraft.getMinecraft();
        if(!LegacyConfig.enabled||failure!=null||mc.theWorld==null||mc.renderViewEntity==null)return;
        try{
            shaderPass=AngelicaCompat.shadersActive();
            if(shaderPass&&me.cortex.voxy.legacy.shader.ShaderBridge.active())return;
            if(shaderPass)return; // Do not silently replace an unsupported pack's lighting with Voxy lighting.
            if(!GL.getCapabilities().OpenGL46){failure="OpenGL 4.6 is required by this Voxy renderer";ForgeVoxy.LOG.warn(failure);return;}
            try(var state=new GlStateFrame()){
                if(session==null){session=new LegacySession(mc.theWorld,cacheDirectory());ForgeVoxy.LOG.info("Voxy legacy session started in dimension {}",mc.theWorld.provider.dimensionId);}
                session.render(event.partialTicks);
            }
        }catch(java.io.IOException|RuntimeException|LinkageError e){fail(e);}
    }
    @SubscribeEvent(priority=cpw.mods.fml.common.eventhandler.EventPriority.LOWEST)
    public void fog(EntityViewRenderEvent.RenderFogEvent event) {
        // This Forge event is dispatched on the normal linear-fog path only.
        // Keep blindness, liquids, clouds, void fog and dimension fog intact.
        if (session==null || !LegacyConfig.enabled || failure!=null || AngelicaCompat.shadersActive()) return;
        var mc=Minecraft.getMinecraft();
        if(event.fogMode<0 || event.farPlaneDistance<mc.gameSettings.renderDistanceChunks*16-1
            || mc.theWorld.provider.doesXZShowFog((int)event.entity.posX,(int)event.entity.posZ)) return;
        AngelicaCompat.extendTerrainFog(Math.max(event.farPlaneDistance,LegacyConfig.distance*16));
    }
    @SubscribeEvent public void loaded(ChunkEvent.Load event){if(session!=null&&event.world==session.clientWorld)session.loaded(event.getChunk());}
    @SubscribeEvent public void unloaded(ChunkEvent.Unload event){if(session!=null&&event.world==session.clientWorld)session.unloaded(event.getChunk());}
    @SubscribeEvent public void debug(RenderGameOverlayEvent.Text event){
        if(Minecraft.getMinecraft().gameSettings.showDebugInfo){event.left.add(status());if(session!=null)session.debug(event.left);}
    }
    private Path cacheDirectory(){
        var mc=Minecraft.getMinecraft();String identity;
        if(mc.getIntegratedServer()!=null)identity="singleplayer:"+mc.getIntegratedServer().getFolderName();
        else if(mc.func_147104_D()!=null)identity="multiplayer:"+mc.func_147104_D().serverIP.toLowerCase(Locale.ROOT);
        else identity="session:"+UUID.randomUUID();
        try{
            byte[] digest=MessageDigest.getInstance("SHA-256").digest((identity+"/"+LegacyConfig.cacheNamespace).getBytes(StandardCharsets.UTF_8));
            String key=HexFormat.of().formatHex(digest);
            return mc.mcDataDir.toPath().resolve("voxy-forge1710-cache-v3").resolve(key).resolve("dim-"+mc.theWorld.provider.dimensionId);
        }catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    public static void shaderRender(int pass) {
        var client=instance;var mc=Minecraft.getMinecraft();
        if(client==null||client.failure!=null||!LegacyConfig.enabled||mc.theWorld==null||mc.renderViewEntity==null)return;
        try {
            if(client.session==null)client.session=new LegacySession(mc.theWorld,client.cacheDirectory());
            client.session.renderShader(pass);
        } catch(java.io.IOException|RuntimeException|LinkageError e){client.fail(e);}
    }
    private void fail(Throwable error){
        failure=error.getClass().getSimpleName()+": "+error.getMessage();ForgeVoxy.LOG.error("Voxy disabled after an error",error);close();
    }
    private void close(){
        if(session==null)return;
        var old=session;session=null;
        try(var state=new GlStateFrame()){old.close();}
        catch(RuntimeException e){ForgeVoxy.LOG.error("Error shutting down Voxy",e);}
    }
}
