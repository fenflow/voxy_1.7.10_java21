package me.cortex.voxy.legacy;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.relauncher.Side;
import org.apache.logging.log4j.Logger;

@Mod(modid="voxy",name="Voxy local backport",version="0.1.6-1.7.10-local.4",acceptedMinecraftVersions="[1.7.10]",acceptableRemoteVersions="*",
    dependencies="required-after:angelica@[2.1.61,);required-after:lwjgl3ify@[3.0.31,);required-after:gtnhlib")
public final class ForgeVoxy {
    public static Logger LOG;
    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event){
        LOG=event.getModLog();
        if(event.getSide()!=Side.CLIENT)return;
        LegacyConfig.load(event.getSuggestedConfigurationFile());
        LegacyClient.install();
        LOG.info("Voxy local Forge backport loaded. Experimental; basic Angelica shader compatibility enabled.");
    }
}
