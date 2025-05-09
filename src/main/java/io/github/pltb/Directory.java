package io.github.pltb;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Directory {

    private final int startingBlockNumber;

    private final Map<String, FileMetadata> fileNameToMetadata;
    private static final Charset SERDE_CHARSET = StandardCharsets.UTF_8;

    public Directory(int startingBlockNumber) {
        this.startingBlockNumber = startingBlockNumber;
        this.fileNameToMetadata = new HashMap<>();
    }

    public Directory(int startingBlockNumber, Map<String, FileMetadata> fileNameToMetadata) {
        this.startingBlockNumber = startingBlockNumber;
        this.fileNameToMetadata = fileNameToMetadata;
    }

    public byte[] toBytes() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.fileNameToMetadata.size());
        sb.append("\n");
        for (Map.Entry<String, FileMetadata> entry : this.fileNameToMetadata.entrySet()) {
            sb.append(entry.getKey());
            sb.append("\n");
            sb.append(entry.getValue().getType());
            sb.append("\n");
            sb.append(entry.getValue().getStartingBlockNumber());
            sb.append("\n");
            sb.append(entry.getValue().getFileSize());
            sb.append("\n");
        }

        return sb.toString().getBytes(SERDE_CHARSET);
    }

    public static Directory fromBytes(byte[] bytes) {
        var fileNameToMetadata = new HashMap<String, FileMetadata>();
        String dirStructure = new String(bytes, SERDE_CHARSET);
        Scanner scanner = new Scanner(dirStructure);
        int numOfEntries = Integer.parseInt(scanner.nextLine());
        for (int i = 0; i < numOfEntries; i++) {
            String fileName = scanner.nextLine();
            FileType fileType = FileType.valueOf(scanner.nextLine());
            int startingBlockNumber = Integer.parseInt(scanner.nextLine());
            int fileSize = Integer.parseInt(scanner.nextLine());
            fileNameToMetadata.put(fileName, new FileMetadata(fileType, startingBlockNumber, fileSize));
        }
        scanner.close();
        return new Directory(0, fileNameToMetadata);
    }

    public long getDirSizeBytes() {
        var totalBytes = 0;
        for (FileMetadata fileMetadata: fileNameToMetadata.values()) {
            totalBytes += fileMetadata.getFileSize();
        }
        return totalBytes;
    }

    public void  addFile(String fileName, int startingBlock, int fileSize) {
        this.fileNameToMetadata.put(fileName, new FileMetadata(FileType.FILE, startingBlock, fileSize));
    }

    public void move(String oldFilePath, String newFilePath) {
        var fileMeta = this.fileNameToMetadata.get(oldFilePath);
        this.fileNameToMetadata.put(newFilePath, fileMeta);
        this.fileNameToMetadata.remove(oldFilePath);
        // todo: process absent files
    }

    public void removeFile(String fileName) {
        this.fileNameToMetadata.remove(fileName);
    }

    public int getStartingBlockNumber() {
        return startingBlockNumber;
    }

    public int getFileStartingBlock(String filePath) {
        return fileNameToMetadata.get(filePath).getStartingBlockNumber();
    }

    public boolean isFileExists(String filePath) {
        return this.fileNameToMetadata.containsKey(filePath);
    }

    public int getFileSize(String fileName) {
        return fileNameToMetadata.get(fileName).getFileSize();
    }

    public List<String> getFileNames() {
        return this.fileNameToMetadata.keySet().stream().toList();
    }

    public Optional<FileMetadata> getFileMetadata(String filePath) {
        return Optional.ofNullable(this.fileNameToMetadata.get(filePath));
    }
}
