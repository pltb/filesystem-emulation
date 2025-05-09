package io.github.pltb;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.*;

public class FileSystemImpl implements FileSystem {

    // config constants
    final private static int BLOCK_SIZE_BYTES = 1024;
    final private static int FAT_ADDR_SIZE_BYTES = 4;

    // filesystem components
    final private BlockDevice blockDevice;
    final private int[] fileAllocationTable;
    final private Superblock superblock;
    final private Directory rootDir;

    final private byte[] END_MARKER_BYTES = {0, 0, 0, 0};
    final private byte[] FREE_MARKER_BYTES = ByteBuffer.allocate(4).putInt(-1).array();

    // todo: combine the constructors?
    private FileSystemImpl(BlockDevice blockDevice,
                           Superblock superblock,
                           int[] fileAllocationTable) throws IOException {
        this.blockDevice = blockDevice;
        this.superblock = superblock;
        this.fileAllocationTable = fileAllocationTable;
        this.rootDir = restoreDirFromDisk();
    }

    private FileSystemImpl(BlockDevice blockDevice,
                           Superblock superblock,
                           int[] fileAllocationTable,
                           Directory rootDir) {
        this.blockDevice = blockDevice;
        this.superblock = superblock;
        this.fileAllocationTable = fileAllocationTable;
        this.rootDir = rootDir;
    }

    // factory methods
    public static FileSystemImpl createNew(File containerFile, long maxCapacity) throws IOException {
        var blockDevice = BlockDeviceImpl.createNew(containerFile, maxCapacity);

        var fileAllocationTableOffset = Superblock.SUPERBLOCK_SIZE_BYTES;
        var fatEntriesNum = calculateNumFatEntries(maxCapacity);
        var dataRegionOffset = fileAllocationTableOffset + fatEntriesNum * FAT_ADDR_SIZE_BYTES;

        var superblock = new Superblock(fileAllocationTableOffset, fatEntriesNum, dataRegionOffset, maxCapacity);
        var fileAllocationTable = new int[fatEntriesNum];
        Arrays.fill(fileAllocationTable, -1);
        var rootDir = new Directory(0);

        blockDevice.storeBlock(0, superblock.toBytes());
        blockDevice.storeBlock(Superblock.SUPERBLOCK_SIZE_BYTES, fatToByteArray(fileAllocationTable));

        var fs = new FileSystemImpl(blockDevice, superblock, fileAllocationTable, rootDir);
        fs.flushRootDir();

        return fs;
    }

    public static FileSystemImpl loadFromContainer(File containerFile) throws IOException {
        var blockDevice = BlockDeviceImpl.attachToFile(containerFile);
        // todo: move superblock to block device?
        var superblock = Superblock.fromBytes(blockDevice.readBlock(0, Superblock.SUPERBLOCK_SIZE_BYTES));
        int[] fileAllocationTable = fatFromByteArray(blockDevice.readBlock(superblock.getFileAllocationTableOffset(), superblock.getFileAllocationTableNumEntries() * FAT_ADDR_SIZE_BYTES));
        return new FileSystemImpl(blockDevice, superblock, fileAllocationTable);
    }

    @Override
    public void compact() throws IOException {
        this.blockDevice.execWithLock(() -> {
            var lastFreeBlockNum = getLastFreeBlockNum();
            var files = unsafeListFiles();
            for (String file : files) {
                Optional<byte[]> fileBytesOpt = unsafeReadFile(file);
                if (fileBytesOpt.isPresent()) {
                    unsafeDeleteFile(file);
                    unsafeCreateFile(file);
                    unsafeAppendToFile(file, fileBytesOpt.get());
                }
            }

            lastFreeBlockNum = getLastFreeBlockNum();
            if (lastFreeBlockNum > 0) {
                var newFileSize = superblock.getDataRegionOffset() + BLOCK_SIZE_BYTES * lastFreeBlockNum;
                blockDevice.truncate(newFileSize);
            }
        });
    }

    @Override
    public int appendToFile(String fileName, byte[] bytes) throws IOException {
        return this.blockDevice.execWithLock(() -> this.unsafeAppendToFile(fileName, bytes));
    }

    public int unsafeAppendToFile(String fileName, byte[] bytes) throws IOException {
        var fileOpt = rootDir.getFileMetadata(fileName);
        if (fileOpt.isEmpty()) {
            return -1;
        }

        var file = fileOpt.get();
        var registeredStartingBlockNumber = file.getStartingBlockNumber();

        var firstFreeBlockNumber = findFirstFreeBlockFromInclusive(0);
        if (registeredStartingBlockNumber >= 0) {
            fileAllocationTable[registeredStartingBlockNumber] = firstFreeBlockNumber;
        }

        writeBytesToDevice(firstFreeBlockNumber, bytes, false, file.getFileSize() % BLOCK_SIZE_BYTES);

        rootDir.addFile(fileName, firstFreeBlockNumber, file.getFileSize() + bytes.length);
        flushRootDir();
        return 1;
    }

    @Override
    public int createFile(String filePath) throws IOException {
        return this.blockDevice.execWithLock(() -> this.unsafeCreateFile(filePath));
    }

    private int unsafeCreateFile(String filePath) throws IOException {
            this.rootDir.addFile(filePath, -1, 0);
            flushRootDir();
            return 1;
    }

    @Override
    public int writeToFileFromOffset(String filePath, byte[] bytes, int offset) throws IOException {
        return this.blockDevice.execWithLock(() -> {
            var fileOpt = rootDir.getFileMetadata(filePath);
            if (fileOpt.isEmpty()) {
                return -1;
            }

            var file = fileOpt.get();

            if (offset + bytes.length >= file.getFileSize()) {
                return -1;
            }

            var inBlockOffset = offset % BLOCK_SIZE_BYTES;
            var blockOrdinalNumber = offset / BLOCK_SIZE_BYTES;
            var blockNumber = findNthBlockInFile(file.getStartingBlockNumber(), blockOrdinalNumber);

            writeBytesToDevice(blockNumber, bytes, true, inBlockOffset);

            rootDir.addFile(filePath, file.getStartingBlockNumber(), file.getFileSize() + bytes.length);
            flushRootDir();
            return 1;
        });
    }

    @Override
    public List<String> listFilesUnderPrefix(String prefix) throws IOException {
        return this.blockDevice.execWithLock(() -> {
            return this.rootDir.getFileNames().stream().filter(fileName -> fileName.startsWith(prefix)).toList();
        });
    }

    @Override
    public int deleteFile(String fileName) throws IOException {
        return this.blockDevice.execWithLock(() -> this.unsafeDeleteFile(fileName));
    }

    private int unsafeDeleteFile(String fileName) throws IOException {
        if (!rootDir.isFileExists(fileName)) {
            return -1;
        }

        var startingBlock = this.rootDir.getFileStartingBlock(fileName);
        this.rootDir.removeFile(fileName);
        flushRootDir();

        eraseBlocksToEndMarker(startingBlock);

        return 1;
    }

    @Override
    public List<String> listFiles() throws IOException {
        return this.blockDevice.execWithLock(this::unsafeListFiles);
    }

    private List<String> unsafeListFiles() {
        return this.rootDir.getFileNames();
    }

    @Override
    public int moveFile(String oldFilePath, String newFilePath) throws IOException {
        return this.blockDevice.execWithLock(() -> {
            rootDir.move(oldFilePath, newFilePath);
            flushRootDir();
            return 1;
        });
    }

    @Override
    public void close() throws IOException {
        blockDevice.close();
    }

    @Override
    public Optional<byte[]> readFile(String fileName) throws IOException {
        return this.blockDevice.execWithLock(() -> this.unsafeReadFile(fileName));
    }

    public Optional<byte[]> unsafeReadFile(String fileName) throws IOException {
            if (!rootDir.isFileExists(fileName)) {
                return Optional.empty();
            }

            var nextBlockNumber = rootDir.getFileStartingBlock(fileName);
            var fileSize = rootDir.getFileSize(fileName);
            if (fileSize == 0) {
                return Optional.of(new byte[0]);
            }

            ByteBuffer bb = ByteBuffer.allocate(fileSize);
            int bytesSeen = 0;

            while (bytesSeen < fileSize) {
                var block = blockDevice.readBlock(superblock.getDataRegionOffset() + BLOCK_SIZE_BYTES * nextBlockNumber, BLOCK_SIZE_BYTES);
                if (bytesSeen + BLOCK_SIZE_BYTES <= bb.limit()) {
                    bb.put(block);
                } else {
                    // if end of file, need to truncate
                    int newLength = bb.limit() - bytesSeen;
                    byte[] truncated = new byte[newLength];
                    System.arraycopy(block, 0, truncated, 0, newLength);
                    bb.put(truncated);
                }

                nextBlockNumber = findNextConnectedBlock(nextBlockNumber);
                bytesSeen += BLOCK_SIZE_BYTES;
            }

            return Optional.of(bb.array());
    }

    @Override
    public long getFreeSpaceBytes() throws IOException {
        return this.blockDevice.execWithLock(() -> {
            return this.superblock.getMaxAddressableSpaceBytes() - this.rootDir.getDirSizeBytes();
        });
    }

    private int writeBytesToDevice(int firstBlock, byte[] bytes, boolean overwrite, int offsetInBlock) throws IOException {
        var currentBlockNumber = firstBlock;
        var bytesLeft = bytes.length;
        var sourceDataOffset = 0;
        var inBlockOffset = offsetInBlock;

        if (offsetInBlock >= BLOCK_SIZE_BYTES) {
            throw new RuntimeException("offset cannot be bigger than the block size");
        }

        while (bytesLeft > 0) {
            var nextFreeBlock = -1;
            if (bytesLeft > BLOCK_SIZE_BYTES) {
                if (overwrite) {
                    nextFreeBlock = findNextBlockSameFileOrAllocateNew(currentBlockNumber);
                } else {
                    nextFreeBlock = findFirstFreeBlockFromExclusive(currentBlockNumber);
                }
                fileAllocationTable[currentBlockNumber] = nextFreeBlock;
                var fatPointerBytes = ByteBuffer.allocate(4).putInt(nextFreeBlock).array();
                blockDevice.storeBlock(superblock.getFileAllocationTableOffset() + FAT_ADDR_SIZE_BYTES * currentBlockNumber, fatPointerBytes);
            } else {
                fileAllocationTable[currentBlockNumber] = 0; // denote end of file
                blockDevice.storeBlock(superblock.getFileAllocationTableOffset() + FAT_ADDR_SIZE_BYTES * currentBlockNumber, END_MARKER_BYTES);
            }

            var dataBlockOffset = superblock.getDataRegionOffset() + BLOCK_SIZE_BYTES * currentBlockNumber;
            var bytesToWrite = BLOCK_SIZE_BYTES - inBlockOffset;
            blockDevice.storeBlock(dataBlockOffset + inBlockOffset, Arrays.copyOfRange(bytes, sourceDataOffset, Math.min(sourceDataOffset + BLOCK_SIZE_BYTES, bytes.length) - inBlockOffset));
            inBlockOffset = 0; // always zero after the first usage

            bytesLeft -= bytesToWrite;
            sourceDataOffset += bytesToWrite;

            if (nextFreeBlock >= 0) {
                currentBlockNumber = nextFreeBlock;
            }
        }

        return 1;
    }

    private int eraseBlocksToEndMarker(int startingBlock) throws IOException {
        var currBlock = startingBlock;
        while (currBlock >= 0 && fileAllocationTable[currBlock] >= 0) {
            var nextBlock = findNextConnectedBlock(currBlock);
            fileAllocationTable[currBlock] = -1;
            blockDevice.storeBlock(superblock.getFileAllocationTableOffset() + FAT_ADDR_SIZE_BYTES * currBlock, FREE_MARKER_BYTES);
            currBlock = nextBlock;
        }
        return currBlock;
    }

    private int dropRootDir() throws IOException {
        return eraseBlocksToEndMarker(this.rootDir.getStartingBlockNumber());
    }

    private int flushRootDir() throws IOException {
        dropRootDir();
        var currentFreeBlockNumber = rootDir.getStartingBlockNumber();
        var serializedDirBytes = rootDir.toBytes();
        writeBytesToDevice(currentFreeBlockNumber, serializedDirBytes, false, 0);
        return 1;
    }

    private int findFirstFreeBlockFromExclusive(int from) {
        // fixme: starting point inclusive/exclusive?
        for (int i = from + 1; i < fileAllocationTable.length; i++) {
            if (fileAllocationTable[i] == -1) {
                return i;
            }
        }

        throw new RuntimeException("no free space left");
    }

    private int findFirstFreeBlockFromInclusive(int from) {
        // fixme: starting point inclusive/exclusive?
        for (int i = from; i < fileAllocationTable.length; i++) {
            if (fileAllocationTable[i] == -1) {
                return i;
            }
        }

        throw new RuntimeException("no free space left");
    }

    private int findNthBlockInFile(int startingBlock, int n) {
        var currBlock = startingBlock;
        for (int i = 0; i < n; i++) {
            if (fileAllocationTable[currBlock] < 0) {
                throw new RuntimeException("unexpected end of file");
            }
            currBlock = fileAllocationTable[currBlock];
        }

        return currBlock;
    }

    private int findNextBlockSameFileOrAllocateNew(int from) {
        if (fileAllocationTable[from] > 0) {
            return fileAllocationTable[from];
        }

        if (fileAllocationTable[from] == 0) {
            var nextFreeBlock = findFirstFreeBlockFromExclusive(from);
            fileAllocationTable[from] = nextFreeBlock;
            return nextFreeBlock;
        }

        return findFirstFreeBlockFromExclusive(from);
    }

    private int findNextConnectedBlock(int from) {
        if (fileAllocationTable[from] > 0) {
            return fileAllocationTable[from];
        }

        return -1;
    }

    private static int calculateNumFatEntries(long deviceSize) {
        var dataRegionSize = (deviceSize * 0.8);
        return (int) Math.ceil(dataRegionSize / BLOCK_SIZE_BYTES);
    }

    private static byte[] fatToByteArray(int[] fat) {
        ByteBuffer bb = ByteBuffer.allocate(FAT_ADDR_SIZE_BYTES * fat.length);
        bb.asIntBuffer().put(fat);
        return bb.array();
    }

    private static int[] fatFromByteArray(byte[] fat) {
        IntBuffer intBuf =
                ByteBuffer.wrap(fat)
                        .order(ByteOrder.BIG_ENDIAN)
                        .asIntBuffer();


        int[] array = new int[intBuf.limit()];
        intBuf.get(array, 0, intBuf.limit());

        return array;
    }

    private Directory restoreDirFromDisk() throws IOException {
        ByteArrayOutputStream bas = new ByteArrayOutputStream();
        var nextBlockNum = 0;
        while (nextBlockNum >= 0) {
            bas.write(blockDevice.readBlock(superblock.getDataRegionOffset() + BLOCK_SIZE_BYTES * nextBlockNum, BLOCK_SIZE_BYTES));
            nextBlockNum = findNextConnectedBlock(nextBlockNum);
        }
        bas.flush();
        return Directory.fromBytes(bas.toByteArray());
    }

    private int getLastFreeBlockNum() {
        var lastFreeBlockNum = -1;
        for (int i = fileAllocationTable.length - 1; i >= 0; i--) {
            if (fileAllocationTable[i] != -1) {
                break;
            }

            lastFreeBlockNum = i;
        }

        return lastFreeBlockNum;
    }
}
