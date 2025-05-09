package io.github.pltb;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemImplTest {

    @TempDir
    File tempDir;

    @Test
    void testFileWriteAndReadRootDir(){
        try {
            var testString = "asdf";
            String fileReadBack;
            File tempFile = new File(tempDir, "container.fs");

            try (var fs = FileSystemImpl.createNew(tempFile, 2 * 1024 * 1024)) {
                fs.createFile("index.txt");
                fs.appendToFile("index.txt", testString.getBytes());
                fileReadBack = new String(fs.readFile("index.txt").get());
                assertEquals(testString, fileReadBack);
            }

            try (var fs = FileSystemImpl.loadFromContainer(tempFile)) {
                fileReadBack = new String(fs.readFile("index.txt").get());
                assertEquals(testString, fileReadBack);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testDumpAllFiles(){
        try {
            File tempFile = new File(tempDir, "container1.fs");
            var rootPath = Path.of(".");
            List<Path> files = new ArrayList<>();
            getFileNames(files, rootPath);


            try (var fs = FileSystemImpl.createNew(tempFile, 2 * 1024 * 1024)) {
                for (Path file : files) {
                    fs.createFile(file.toAbsolutePath().toString());
                    fs.appendToFile(file.toAbsolutePath().toString(), Files.readAllBytes(file));
                }

                var filesStored = fs.listFiles();
                var filesToDelete = filesStored.subList(0, filesStored.size() - filesStored.size() / 3);

                for (String fileToDelete : filesToDelete) {
                    fs.deleteFile(fileToDelete);
                }

                var fileSize = Files.size(tempFile.toPath());
                fs.compact();
                var newFileSize = Files.size(tempFile.toPath());
                assertTrue(newFileSize < fileSize);

                for (Path file : files) {
                    fs.createFile("subfolder" + file.toAbsolutePath());
                    fs.appendToFile("subfolder" + file.toAbsolutePath(), Files.readAllBytes(file));
                }
            }

            try (var fs = FileSystemImpl.loadFromContainer(tempFile)) {
                for (String fileName : fs.listFiles().stream().filter(fileName -> !fileName.startsWith("subfolder")).toList()) {
                    var file = Path.of(fileName);
                    assertArrayEquals(Files.readAllBytes(file), fs.readFile(fileName).get());
                }

                var filesFromSubfolder = fs.listFilesUnderPrefix("subfolder/");

                for (String fileName : filesFromSubfolder) {
                    var file = Path.of(fileName.replaceFirst("subfolder", ""));
                    assertArrayEquals(Files.readAllBytes(file), fs.readFile(fileName).get());
                }
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<String> IGNORED_DIRS = List.of(".gradle");

    private static List<Path> getFileNames(List<Path> files, Path dir) {
        try(DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path path : stream) {
                if (IGNORED_DIRS.stream().noneMatch(ignoredDir -> path.getFileName().toString().equals(ignoredDir))) {
                    if (path.toFile().isDirectory()) {
                        getFileNames(files, path);
                    } else {
                        files.add(path);
                    }
                }
            }
        } catch(IOException e) {
            e.printStackTrace();
        }

        return files;
    }
}
