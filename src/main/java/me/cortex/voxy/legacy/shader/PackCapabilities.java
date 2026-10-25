package me.cortex.voxy.legacy.shader;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

/** Detect interfaces from pack contents, never product names or version strings. */
final class PackCapabilities {
    static boolean hasDistantTerrain(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var files=Files.walk(path)) { return hasDistantTerrain(files.filter(Files::isRegularFile).map(p->path.relativize(p).toString().replace('\\','/')).toList()); }
        }
        try (var zip=new ZipFile(path.toFile())) { return hasDistantTerrain(zip.stream().map(e->e.getName()).toList()); }
    }
    static boolean hasDistantTerrain(Collection<String> files) {
        var names=new HashSet<>(files);
        return names.stream().anyMatch(n->n.endsWith("/dh_terrain.vsh")&&names.contains(n.substring(0,n.length()-4)+".fsh"));
    }
}
