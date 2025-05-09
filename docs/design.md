## Design

### Basics

The design in based on the [FAT](https://en.wikipedia.org/wiki/File_Allocation_Table) file system designs.

Basic points:
- files are split into logical chunks (blocks), to make resource usage and data operations more efficient
- file data is stored in a dedicated region of the virtual "disk", which is called *data region*
- apart from the data region, there are two disk regions which contain the metadata about the file system:
the superblock and the file allocation table, both of which are explained below.

### File Allocation Table

The main conceptual part of the design is the File Allocation Table (FAT) –
a very simple data structure that maps out the data region and denotes which blocks belong to the same file and which blocks are free,
so that it's possible to reconstruct the files from chunks and to find free blocks for allocation.

FAT is essentially an array of integers, and each element can have one of the following three states:
- `-1` means the corresponding block is free
- `0` means that the block contains the end of some file
- a number greater than 0 – is a number of the block containing the next chunk of the file

### Superblock

The region of the "disk" that:
- has a fixed location (in this case, it is located right at the start of the "disk")
- contains basic metadata about the file system
- disk region offsets
- max disk size
- FAT table size

### Block Device

The file system operates over a simple "block device" that can basically do two things:
- store a byte block at a particular offset
- read a byte block starting from an offset

This "block device" essentially represents a virtual disk
that is split into three contiguous regions:

- the superblock
  - stores the file system metadata (region offsets, max size)
- the file allocation table
  - stores a map of the data region
- data region
  - stores actual file data

### Directories

A decision was taken to implement a single-directory structure (without the _actually_ nested directories), reasons being simplicity and implementation speed.
The implementation can be easily improved to emulate directory nesting.

The root directory is represented via a text file residing in the data region, which contains all file metadata in the following format:

```shell
<number of entries>
<file_name_1>
<file_type_1>
<file_starting_block_1>
<file_size_1>
<file_name_2>
<file_type_2>
<file_starting_block_2>
<file_size_2>
```

### Compaction

The approach to the compaction is very straightforward –
Files are read one by one, and written back into the file system.
The repeated write will result in the files to be written starting from an earlier free block, if there is any.

After that, if any files were moved closer to the start of the data region, the container can be truncated.

### Known drawbacks
- won't support accessing from multiple processes (fixable)
- can potentially be more efficient if using the
[memory-mapping capability](https://docs.oracle.com/javase/8/docs/api/java/nio/channels/FileChannel.html#map-java.nio.channels.FileChannel.MapMode-long-long-) of FileChannel
- there are some obsolete leftovers or things worth refactoring (see todo comments)
- more tests are needed
- logging is missing

### Performance analysis

TODO
