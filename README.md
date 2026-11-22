This is no longer supported, I shifted my focus to developing backport of [Roxy](https://github.com/fenflow/Roxy-1.7.10-Java21) to 1.7.10 since I can actually distribute the jar for that, and it works better.

This is a backport of voxy to forge 1.7.10. I'll be real it's fully done with AI, so take that into consideration. It was tested and created for GregTech: New horizons 2.9.0 beta-3 with Angelica 2.1.61, lwjgl3ify 3.0.31, GTNHLib 0.11.46. Java 21 or newer. Releases are not provided, as voxy is an ARR mod and doesn't allow redistribution.

## Build and test

From PowerShell in this project:

```powershell
.\scripts\build.ps1 -GpuTest
```

The script uses Prism's Java 25 when available, otherwise Java from PATH. Supply `-Java 'C:\path\to\java.exe'` to choose another runtime. Gradle 9.5 requires a recent build JVM and downloads a Java 21 toolchain. Build caches and compiler output go under the current user's temporary directory to avoid the legacy decompiler's Windows Unicode-path issue. Deliverables are copied into `build/libs` and reports into `build/reports`.

Use `voxy-forge1710-0.1.6-1.7.10-local.4.jar` in the client's mods folder. The `-dev.jar` is for development and must not be installed in a normal client. Do not install both. Runtime validation uses the separate copy in `test-instance`. Keep only one enabled Voxy jar in your mods folder. Building the shader adapter requires the target Angelica jar at `test-instance/.minecraft/mods/angelica-2.1.61.jar`; it is compile-only and is not bundled.

## Behavior and controls

Voxy records client-visible chunk sections and renders cached terrain out to the configured distance. Explore terrain first: it does not generate unseen chunks, read region files, or extend a server's view distance.

- `/voxy status`: report whether the renderer is active, paused, or disabled after an error, plus capture and mesh queue counts.
- `/voxy on`, `/voxy off`: toggle the current client session.
- `/voxy reload`: rebuild the current rendering session after an error or resource change.
- F3: capture, ingest, mesh queue, geometry, and model counts.
- `config/voxy.cfg`: persistent enable switch, distance, quality, memory, worker count and capture rate. Restart after editing configuration.

Cache data is separate from Minecraft worlds, under `voxy-forge1710-cache-v3/<world-hash>/dim-<id>`. The cache uses registry-name block palettes with 16-bit metadata, compact biome palettes compatible with EndlessIDs, checksummed sections, atomic file replacement and an exclusive writer lock. Change `cacheNamespace` after a multiplayer map reset. Cached terrain can only reflect block changes the client actually receives.
