package io.github.pltb;

import java.nio.ByteBuffer;

public class Superblock {

    // size: 16 * 3 + 4

    public static final short SUPERBLOCK_SIZE_BYTES = 160;

    private final int fileAllocationTableOffset;
    private final int fileAllocationTableNumEntries;
    private final int dataRegionOffset;
    private final long maxAddressableSpaceBytes;


    public Superblock(int fileAllocationTableOffset, int fileAllocationTableNumEntries, int dataRegionOffset,  long maxAddressableSpaceBytes) {
        this.fileAllocationTableOffset = fileAllocationTableOffset;
        this.dataRegionOffset = dataRegionOffset;
        this.maxAddressableSpaceBytes = maxAddressableSpaceBytes;
        this.fileAllocationTableNumEntries = fileAllocationTableNumEntries;
    }

    public static Superblock fromBytes(byte[] bytes) {
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        return new Superblock(bb.getInt(), bb.getInt(), bb.getInt(), bb.getLong());
    }

    public int getFileAllocationTableOffset() {
        return fileAllocationTableOffset;
    }

    public int getDataRegionOffset() {
        return dataRegionOffset;
    }

    public long getMaxAddressableSpaceBytes() {
        return maxAddressableSpaceBytes;
    }

    public byte[] toBytes() {
        ByteBuffer bb = ByteBuffer.allocate(SUPERBLOCK_SIZE_BYTES);
        bb.putInt(fileAllocationTableOffset).putInt(fileAllocationTableNumEntries).putInt(dataRegionOffset).putLong(maxAddressableSpaceBytes);
        return bb.array();
    }

    public int getFileAllocationTableNumEntries() {
        return fileAllocationTableNumEntries;
    }
}
