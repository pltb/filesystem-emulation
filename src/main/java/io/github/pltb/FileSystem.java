package io.github.pltb;

import java.io.*;
import java.util.*;

public interface FileSystem extends Closeable {

    void compact() throws IOException;

    int appendToFile(String fileName, byte[] bytes) throws IOException;


    int createFile(String filePath) throws IOException;

    int writeToFileFromOffset(String filePath, byte[] bytes, int offset) throws IOException;

    List<String> listFilesUnderPrefix(String prefix) throws IOException;

    List<String> listFiles() throws IOException;

    int deleteFile(String fileName) throws IOException;

    int moveFile(String oldFilePath, String newFilePath) throws IOException;

    Optional<byte[]> readFile(String fileName) throws IOException;

    long getFreeSpaceBytes() throws IOException;
}
