package me.cortex.voxy.legacy;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.util.zip.*;
import me.cortex.voxy.common.storage.StorageBackend;
import org.lwjgl.system.MemoryUtil;

/** A separate versioned cache. Atomic replacement prevents partially written sections. */
public final class FileStorageBackend extends StorageBackend {
    private static final int MAX_SECTION_BYTES = 400000;
    private final Path root;
    private final Object[] sectionLocks=java.util.stream.IntStream.range(0,64).mapToObj(i->new Object()).toArray();
    private Object lockFor(long key){return sectionLocks[Long.hashCode(key^(key>>>21)^(key>>>42))&63];}
    private final FileChannel lockChannel;
    private final FileLock lock;
    public FileStorageBackend(Path root) throws IOException {
        this.root = root;
        Files.createDirectories(root.resolve("sections")); Files.createDirectories(root.resolve("palette"));
        lockChannel = FileChannel.open(root.resolve("cache.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock acquired;
        try { acquired = lockChannel.tryLock(); }
        catch (RuntimeException | IOException e) { lockChannel.close(); throw e; }
        if (acquired == null) { lockChannel.close(); throw new IOException("Voxy cache is already in use"); }
        lock = acquired;
    }
    private Path section(long key) { String name = Long.toUnsignedString(key, 16); return root.resolve("sections").resolve(name.substring(0, Math.min(2, name.length()))).resolve(name + ".gz"); }
    public ByteBuffer getSectionData(long key) {
      synchronized(lockFor(key)){
        Path file = section(key); if (!Files.exists(file)) return null;
        try (var in = new GZIPInputStream(Files.newInputStream(file))) {
            byte[] bytes = in.readNBytes(MAX_SECTION_BYTES + 1);
            if (bytes.length > MAX_SECTION_BYTES) throw new IOException("Oversize Voxy section");
            return MemoryUtil.memAlloc(bytes.length).put(bytes).flip();
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    }
    public void setSectionData(long key, ByteBuffer data) {
      synchronized(lockFor(key)){
        try {
            var bytes = new ByteArrayOutputStream();
            try (var out = new GZIPOutputStream(bytes)) { byte[] raw = new byte[data.remaining()]; data.duplicate().get(raw); out.write(raw); }
            atomicWrite(section(key), bytes.toByteArray());
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    }
    private static void atomicWrite(Path target, byte[] data) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), "pending-", ".tmp");
        try {
            Files.write(temp, data);
            AtomicFileReplacement.replace(temp,target,(from,to)->{
                try { Files.move(from,to,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
                catch(AtomicMoveNotSupportedException e){Files.move(from,to,StandardCopyOption.REPLACE_EXISTING);}
            });
        } finally { Files.deleteIfExists(temp); }
    }
    public void deleteSectionData(long key) { synchronized(lockFor(key)){ try { Files.deleteIfExists(section(key)); } catch (IOException e) { throw new UncheckedIOException(e); } } }
    public void putIdMapping(int id, ByteBuffer data) {
        byte[] bytes = new byte[data.remaining()]; data.duplicate().get(bytes);
        try { atomicWrite(root.resolve("palette").resolve(id + ".bin"), bytes); }
        catch (IOException e) { throw new UncheckedIOException(e); }
    }
    public Int2ObjectOpenHashMap<byte[]> getIdMappingsData() {
        var result = new Int2ObjectOpenHashMap<byte[]>();
        try (var stream = Files.newDirectoryStream(root.resolve("palette"), "*.bin")) {
            for (Path file : stream) {
                if (Files.size(file) > 65540) throw new IOException("Invalid palette entry size");
                int id = Integer.parseInt(file.getFileName().toString().replace(".bin", ""));
                result.put(id, Files.readAllBytes(file));
            }
        } catch (IOException e) { throw new UncheckedIOException(e); }
        return result;
    }
    public void flush() { }
    public void close() { try { lock.release(); lockChannel.close(); } catch (IOException e) { throw new UncheckedIOException(e); } }
}
