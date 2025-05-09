package io.github.pltb;

public class FileMetadata {
    private FileType type;
    private int startingBlockNumber;
    private int fileSize;

    public FileMetadata(FileType type, int startingBlockNumber, int fileSize) {
        this.type = type;
        this.startingBlockNumber = startingBlockNumber;
        this.fileSize = fileSize;
    }

    public int getStartingBlockNumber() {
        return startingBlockNumber;
    }

    public FileType getType() {
        return type;
    }

    public int getFileSize() {
        return fileSize;
    }
}
