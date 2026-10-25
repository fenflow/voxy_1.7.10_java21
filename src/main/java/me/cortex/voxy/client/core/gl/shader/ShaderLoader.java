package me.cortex.voxy.client.core.gl.shader;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

public class ShaderLoader {
    private static final Pattern IMPORT = Pattern.compile("^\\s*#import\\s+<([^>]+)>\\s*$");
    public static String parse(String id) { return load(id, new HashSet<>()); }
    private static String load(String id, Set<String> stack) {
        if (!id.matches("[a-z0-9_]+:[a-zA-Z0-9_/.-]+") || id.contains("..") || !stack.add(id))
            throw new IllegalArgumentException("Invalid or recursive shader import: " + id);
        String[] pair = id.split(":", 2);
        String resource = "/assets/" + pair[0] + "/shaders/" + pair[1];
        try (InputStream stream = ShaderLoader.class.getResourceAsStream(resource)) {
            if (stream == null) throw new FileNotFoundException(resource);
            var result = new StringBuilder();
            for (String line : new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                var match = IMPORT.matcher(line);
                result.append(match.matches() ? load(match.group(1), stack) : line).append('\n');
            }
            return result.toString();
        } catch (IOException e) { throw new UncheckedIOException(e); }
        finally { stack.remove(id); }
    }
}
