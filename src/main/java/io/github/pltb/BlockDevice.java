package io.github.pltb;

import java.io.Closeable;
import java.io.IOException;

public interface BlockDevice extends Closeable {
    void storeBlock(int blockOffset, byte[] block) throws IOException;
    byte[] readBlock(int blockOffset, int blockSize) throws IOException;
    long getSizeInBytes() throws IOException;

    void truncate(long newLength) throws IOException;

    void flush() throws IOException;

    void execWithLock(RunnableIOOperation func) throws IOException;

    <T> T execWithLock(CallableIOOperation<? extends T> func) throws IOException;
}
