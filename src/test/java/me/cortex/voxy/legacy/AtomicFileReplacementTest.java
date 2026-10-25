package me.cortex.voxy.legacy;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class AtomicFileReplacementTest {
    @TempDir Path directory;
    @Test void transientSharingFailureRetainsOldFileUntilSuccessfulReplacement()throws Exception{
        Path old=directory.resolve("cache"),next=directory.resolve("pending");
        Files.writeString(old,"old");Files.writeString(next,"new");var attempts=new AtomicInteger();
        AtomicFileReplacement.replace(next,old,(from,to)->{
            if(attempts.incrementAndGet()<3){assertEquals("old",Files.readString(to));throw new AccessDeniedException(to.toString());}
            Files.move(from,to,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        });
        assertEquals(3,attempts.get());assertEquals("new",Files.readString(old));
    }
    @Test void permanentFailureIsReportedWithoutDeletingExistingCache()throws Exception{
        Path old=directory.resolve("cache"),next=directory.resolve("pending");
        Files.writeString(old,"old");Files.writeString(next,"new");
        assertThrows(AccessDeniedException.class,()->AtomicFileReplacement.replace(next,old,(from,to)->{throw new AccessDeniedException(to.toString());}));
        assertEquals("old",Files.readString(old));assertEquals("new",Files.readString(next));
    }
}
