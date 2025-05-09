package io.github.pltb;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;

public class BlockDeviceImpl implements BlockDevice {

    private final FileChannel fileChannel;

    private long maxAddressableSpaceBytes;

    private BlockDeviceImpl(FileChannel fileChannel) throws IOException {
        this.fileChannel = fileChannel;
    }

    public static BlockDevice createNew(File containerFile, long maxAddressableSpaceBytes) throws IOException {
        containerFile.createNewFile();
        var fileChannel = FileChannel.open(containerFile.toPath(), StandardOpenOption.WRITE, StandardOpenOption.READ);
        var blockDevice = new BlockDeviceImpl(fileChannel);
        blockDevice.maxAddressableSpaceBytes = maxAddressableSpaceBytes;
        return blockDevice;
    }

    public static BlockDevice attachToFile(File containerFile) throws IOException {
        var fileChannel = FileChannel.open(containerFile.toPath(), StandardOpenOption.WRITE, StandardOpenOption.READ);
        var blockDevice = new BlockDeviceImpl(fileChannel);
        var superblock = Superblock.fromBytes(blockDevice.readBlock(0, Superblock.SUPERBLOCK_SIZE_BYTES));
        blockDevice.maxAddressableSpaceBytes = superblock.getMaxAddressableSpaceBytes();
        return blockDevice;
    }

    @Override
    public void storeBlock(int blockOffset, byte[] block) throws IOException {
        fileChannel.position(blockOffset);
        fileChannel.write(ByteBuffer.wrap(block));
    }

    @Override
    public byte[] readBlock(int blockOffset, int blockSize) throws IOException {
        fileChannel.position(blockOffset);
        ByteBuffer dest = ByteBuffer.allocate(blockSize);
        fileChannel.read(dest);
        return dest.array();
    }

    @Override
    public long getSizeInBytes() throws IOException {
        return this.fileChannel.size();
    }

    @Override
    public void flush() throws IOException {
        this.fileChannel.force(true);
    }

    @Override
    public void execWithLock(RunnableIOOperation func) throws IOException {
        FileLock lock = this.fileChannel.lock();

        try {
            func.run();
        } finally {
            lock.release();
        }
    }

    @Override
    public <T> T execWithLock(CallableIOOperation<? extends T> func) throws IOException {
        FileLock lock = this.fileChannel.lock();

        try {
            return func.run();
        } finally {
            lock.release();
        }
    }

    @Override
    public void close() throws IOException {
        this.fileChannel.close();
    }

    @Override
    public void truncate(long newLength) throws IOException {
        this.fileChannel.truncate(newLength);
    }

    public long getMaxAddressableSpaceBytes() {
        return maxAddressableSpaceBytes;
    }
}
