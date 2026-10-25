package me.cortex.voxy.legacy;

import java.io.IOException;
import java.nio.file.*;

/** Windows scanners/sync clients can briefly prevent a rename. Preserve the old file and retry. */
final class AtomicFileReplacement {
    interface Move { void run(Path from,Path to)throws IOException; }
    static void replace(Path from,Path to,Move move)throws IOException {
        for(int attempt=0;;attempt++){
            try{move.run(from,to);return;}
            catch(FileSystemException busy){
                if(attempt==7)throw busy;
                try{Thread.sleep(Math.min(250,10L<<attempt));}
                catch(InterruptedException stop){Thread.currentThread().interrupt();throw new IOException("Interrupted replacing cache file",stop);}
            }
        }
    }
}
