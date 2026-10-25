package me.cortex.voxy.legacy;

import java.io.File;
import net.minecraftforge.common.config.Configuration;

public final class LegacyConfig {
    public static boolean enabled=true,shaderCompatibility=true;
    public static int distance,quality,threads,geometryMiB,sectionsPerTick;
    public static String cacheNamespace;
    public static void load(File file){
        Configuration cfg=new Configuration(file);cfg.load();
        enabled=cfg.getBoolean("enabled","client",true,"Enable the experimental Voxy backport.");
        shaderCompatibility=cfg.getBoolean("shaderCompatibility","client",true,"Use the selected shader pack's terrain lighting, fog and shadow projection. Tries distant-terrain or standard terrain interfaces automatically. Unsupported programs pause LOD rendering; check /voxy status.");
        distance=cfg.getInt("renderDistanceChunks","client",128,16,512,"Distance of previously observed terrain. Does not generate chunks or extend server view distance.");
        quality=cfg.getInt("qualityScale","client",4,1,16,"Distance between Voxy LOD transitions; larger values use more memory.");
        threads=cfg.getInt("meshWorkers","client",2,1,8,"Background mesh workers.");
        geometryMiB=cfg.getInt("geometryMiB","client",128,32,512,"GPU geometry buffer size; additional model and transfer buffers are also allocated.");
        sectionsPerTick=cfg.getInt("sectionsPerTick","client",4,1,32,"Baseline capture sections per tick. Backlog catch-up may increase this to 64 within an 8 ms budget.");
        cacheNamespace=cfg.getString("cacheNamespace","client","default","Change after a multiplayer map reset to prevent reusing old terrain.");
        if(cfg.hasChanged())cfg.save();
    }
}
