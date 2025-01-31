# Archive Patcher Documentation

Copyright 2016 Google LLC. All rights reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

----

# Table of Contents
* [Introduction](#introduction)
* [How It Works](#how-it-works)
 * [Generating a Patch](#generating-a-patch)
 * [Applying a Patch](#applying-a-patch)
 * [Handled Cases](#handled-cases)
* [Sample Code: Generating a Patch](#sample-code-generating-a-patch)
* [Sample Code: Applying a Patch](#sample-code-applying-a-patch)
* [Background](#background)
* [The File-by-File v1 Patch Format](#the-file-by-file-v1-patch-format)
 * [Old Archive Uncompression Op](#old-archive-uncompression-op)
 * [New Archive Recompression Op](#new-archive-recompression-op)
 * [Compression Settings](#compression-settings)
 * [Compatibility Window](#compatibility-window)
 * [Delta Descriptor Record](#delta-descriptor-record)
* [Appendix](#appendix)
 * [Interesting Obstacles to Patching Archives](#interesting-obstacles-to-patching-archives)
 * [Areas For Improvement](#areas-for-improvement)
* [Acknowledgements](#acknowledgements)

# Introduction
**Archive-patcher is an open-source project that allows space-efficient patching of zip archives.** Many common distribution formats (such as jar and apk) are valid zip archives; archive-patcher works with all of them.

Because the patching process examines each individual file within the input archives, we refer to the process as **File-by-File patching** and an individual patch generated by that process as a **File-by-File patch**. Archive-patcher processes almost all zip files, but it is most efficient for zip files created with "standard" tools like PKWARE's 'zip', Oracle's 'jar', and Google's 'aapt'.

By design, **File-by-File patches are uncompressed**. This allows freedom in choosing the best compression algorithms for a given use case. It is usually best to compress the patches for storage or transport.

> *Note: Archive-patcher does not currently handle 'zip64' archives (archives supporting more than 65,535 files or containing files larger than 4GB in size).*

# How It Works
Archive-patcher **transforms** archives into a **delta-friendly space** to generate and apply a delta. This transformation involves uncompressing the compressed content that has changed, while leaving everything else alone. The patch applier then recompresses the content that has changed to create a perfect binary copy of the original input file. In v1, bsdiff is the delta algorithm used within the delta-friendly space. Much more information on this subject is available in the [Appendix](#appendix).

Diagrams and examples follow. In these examples we will use an old archive and a new archive, each containing 3 files: foo.txt, bar.xml, and baz.lib:

* **foo.txt** has changed its content between the old and new archives. It is uncompressed from both the old and new archives during transformation to the delta-friendly space. This will allow the delta between v1 and v2 of the file to be encoded efficiently.
* **bar.xml** has also changed its content between the old and new archives. It is already uncompressed in the old and new archives, so it is left alone during transformation to the delta-friendly space. The delta between v1 and v2 of the file can already be encoded efficiently.
* **baz.lib** has *not* changed between the old and new archives. It is left alone during transformation to the delta-friendly space because it has not changed and the delta for an unchanged file is trivially empty.

## Generating a Patch
1. Determine which files in the new archive have changed from the old archive.
2. Determine which of the changed files from (1) have deflate settings that can be determined and record those settings.
3. Determine the original offsets and lengths of all files in (2) in both the old and new archives.
4. Create delta-friendly versions of both the old and new archives, uncompressing the files from (2). The resulting intermediate artifacts are called **delta-friendly blobs**; they are no longer valid zip archives.
5. Generate a delta between the old and new delta-friendly blobs from (4).
6. Output the patch carrying the data from (2), (3) and (5).

```
File-by-File v1: Patch Generation Overview


                      Delta-Friendly       Delta-Friendly
   Old Archive           Old Blob             New Blob            New Archive
 ----------------    ----------------     ----------------    ----------------
 |   foo.txt    |    |   foo.txt    |     |   foo.txt    |    |   foo.txt    |
 |   version 1  |    |   version 1  |     |   version 2  |    |   version 2  |
 | (compressed) |    |(uncompressed)|     |(uncompressed)|    | (compressed) |
 |--------------|    |              |     |              |    |--------------|
 |   bar.xml    |    |              |     |              |    |   bar.xml    |
 |   version 1  |    |--------------|     |--------------|    |   version 2  |
 |(uncompressed)|--->|   bar.xml    |--┬--|   bar.xml    |<---|(uncompressed)|
 |--------------|    |   version 1  |  |  |   version 2  |    |--------------|
 |   baz.lib    |    |(uncompressed)|  |  |(uncompressed)|    |   baz.lib    |
 |   version 1  |    |--------------|  |  |--------------|    |   version 1  |
 | (compressed) |    |   baz.lib    |  |  |   baz.lib    |    | (compressed) |
 ----------------    |   version 1  |  |  |   version 1  |    ----------------
        |            | (compressed) |  |  | (compressed) |            |
        |            ----------------  |  ----------------            |
        v                              v                              v
 ----------------                 ----------                  ----------------
 |Uncompression |                 | delta  |                  |Recompression |
 |   metadata   |                 ----------                  |   metadata   |
 ----------------                      |                      ----------------
        |                              v                              |
        |                   ----------------------                    |
        └------------------>|  File-by-File v1   |<-------------------┘
                            |       Patch        |
                            ----------------------
```

## Applying a Patch
1. Reconstruct the delta-friendly old blob using information from the patch.
2. Apply the delta to the delta-friendly old blob generated in (1). This generates the delta-friendly new blob.
3. Recompress the files in the delta-friendly new blob using information from the patch. The result is the "new archive" that was the original input to the patch generator.

```
File-by-File v1: Patch Application Overview


                      Delta-Friendly       Delta-Friendly
   Old Archive           Old Blob             New Blob           New Archive
 ----------------    ----------------     ---------------     ----------------
 |   foo.txt    |    |   foo.txt    |     |   foo.txt    |    |   foo.txt    |
 |   version 1  |    |   version 1  |     |   version 2  |    |   version 2  |
 | (compressed) |    |(uncompressed)|     |(uncompressed)|    | (compressed) |
 |--------------|    |              |     |              |    |--------------|
 |   bar.xml    |    |              |     |              |    |   bar.xml    |
 |   version 1  |    |--------------|     |--------------|    |   version 2  |
 |(uncompressed)|-┬->|   bar.xml    |     |   bar.xml    |-┬->|(uncompressed)|
 |--------------| |  |   version 1  |     |   version 2  | |  |--------------|
 |   baz.lib    | |  |(uncompressed)|     |(uncompressed)| |  |   baz.lib    |
 |   version 1  | |  |--------------|     |--------------| |  |   version 1  |
 | (compressed) | |  |   baz.lib    |     |   baz.lib    | |  | (compressed) |
 ---------------- |  |   version 1  |     |   version 1  | |  ----------------
                  |  | (compressed) |     | (compressed) | |
                  |  ----------------     ---------------- |
                  |         |                    ^         |
 ---------------- |         |     ----------     |         |  ----------------
 |Uncompression |-┘         └---->| delta  |-----┘         └--|Recompression |
 |   metadata   |                 ----------                  |   metadata   |
 ----------------                      ^                      ----------------
        ^                              |                              ^
        |                   ----------------------                    |
        └-------------------|  File-by-File v1   |--------------------┘
                            |       Patch        |
                            ----------------------
```

## Handled Cases
The examples above used two simple archives with 3 common files to help explain the process, but there is significantly more nuance in the implementation. The implementation searches for and handles changes of many types, including some trickier edge cases such as a file that changes compression level, becomes compressed or becomes uncompressed, or is renamed without changes.

Files that are only in the *new* archive are always left alone, and the delta usually encodes them as a literal copy. Files that are only in the *old* archive are similarly left alone, and the delta usually just discards their bytes completely. And of course, files whose deflate settings cannot be inferred are left alone, since they cannot be recompressed and are therefore required to remain in their existing compressed form.

> *Note: The v1 implementation does not detect files that are renamed and changed at the same time. This is the domain of similar-file detection, a feature deemed desirable - but not critical - for v1.*

# Sample Code: Generating a Patch
The following code snippet illustrates how to generate a patch and compress it with deflate compression. The example in the subsequent section shows how to apply such a patch.

```java
import com.google.archivepatcher.generator.FileByFileDeltaGenerator;
import com.google.archivepatcher.shared.DefaultDeflateCompatibilityWindow;
import java.io.File;
import java.io.FileOutputStream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/** Generate a patch; args are old file path, new file path, and patch file path. */
public class SamplePatchGenerator {
  public static void main(String... args) throws Exception {
    if (!new DefaultDeflateCompatibilityWindow().isCompatible()) {
      System.err.println("zlib not compatible on this system");
      System.exit(-1);
    }
    File oldFile = new File(args[0]); // must be a zip archive
    File newFile = new File(args[1]); // must be a zip archive
    Deflater compressor = new Deflater(9, true); // to compress the patch
    try (FileOutputStream patchOut = new FileOutputStream(args[2]);
        DeflaterOutputStream compressedPatchOut =
            new DeflaterOutputStream(patchOut, compressor, 32768)) {
      new FileByFileDeltaGenerator().generateDelta(oldFile, newFile, compressedPatchOut);
      compressedPatchOut.finish();
      compressedPatchOut.flush();
    } finally {
      compressor.end();
    }
  }
}
```

# Sample Code: Applying a Patch
The following code snippet illustrates how to apply a patch that was compressed with deflate compression, as in the previous example.

```java
import com.google.archivepatcher.applier.FileByFileDeltaApplier;
import com.google.archivepatcher.shared.DefaultDeflateCompatibilityWindow;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/** Apply a patch; args are old file path, patch file path, and new file path. */
public class SamplePatchApplier {
  public static void main(String... args) throws Exception {
    if (!new DefaultDeflateCompatibilityWindow().isCompatible()) {
      System.err.println("zlib not compatible on this system");
      System.exit(-1);
    }
    File oldFile = new File(args[0]); // must be a zip archive
    Inflater uncompressor = new Inflater(true); // to uncompress the patch
    try (FileInputStream compressedPatchIn = new FileInputStream(args[1]);
        InflaterInputStream patchIn =
            new InflaterInputStream(compressedPatchIn, uncompressor, 32768);
        FileOutputStream newFileOut = new FileOutputStream(args[2])) {
      new FileByFileDeltaApplier().applyDelta(oldFile, patchIn, newFileOut);
    } finally {
      uncompressor.end();
    }
  }
}
```

# Background
Patching software exists primarily to make updating software or data files **spatially efficient**. This is accomplished by figuring out what has changed between the inputs (usually an old version and a new version of a given file) and transmitting **only the changes** instead of transmitting the entire file. For example, if we wanted to update a dictionary with one new definition, it's much more efficient to send just the one updated definition than to send along a brand new dictionary! A number of excellent algorithms exist to do just this - diff, bsdiff, xdelta and many more.

In order to generate **spatially efficient** patches for zip archives, the content within the zip archives needs to be uncompressed. This necessitates recompressing after applying a patch, and this in turn requires knowing the settings that were originally used to compress the data within the zip archive and being able to reproduce them exactly. These three problems are what make patching zip archives a unique challenge, and their solutions are what make archive-patcher interesting. If you'd like to read more about this now, skip down to [Interesting Obstacles to Patching Archives](#interesting-obstacles-to-patching-archives).

# The File-by-File v1 Patch Format
The v1 patch format is a sequence of bytes described below. Care has been taken to make the format friendly to streaming, so the order of fields in the patch is intended to reflect the order of operations needed to apply the patch. Unless otherwise noted, the following constraints apply:

* All integer fields contain **unsigned**, **big endian**​ values. However:
 * 32-bit integer fields have a maximum value of 2^31 ­- 1 (due to limitations in Java)
 * 64-bit integer fields have a maximum value of 2^63 ­- 1 (due to limitations in Java)

```
|------------------------------------------------------|
| Versioned Identifier (8 bytes) (UTF-8 text)          | Literal: "GFbFv1_0"
|------------------------------------------------------|
| Flags (4 bytes) (currently unused, but reserved)     |
|------------------------------------------------------|
| Delta-friendly old archive size (8 bytes) (uint64)   |
|------------------------------------------------------|
| Num old archive uncompression ops (4 bytes) (uint32) |
|------------------------------------------------------|
| Old archive uncompression op 1...n (variable length) | (see definition below)
|------------------------------------------------------|
| Num new archive recompression ops (4 bytes) (uint32) |
|------------------------------------------------------|
| New archive recompression op 1...n (variable length) | (see definition below)
|------------------------------------------------------|
| Num delta descriptor records (4 bytes) (uint32)      |
|------------------------------------------------------|
| Delta descriptor record 1...n (variable legth)       | (see definition below)
|------------------------------------------------------|
| Delta 1...n (variable length)                        | (see definition below)
|------------------------------------------------------|
```

## Old Archive Uncompression Op
The number of these entries is determined by the "Num old archive uncompression ops" field previously defined. Each entry consists of an offset (from the beginning of the file) and a number of bytes to uncompress. Important notes:

* Entries must be ordered in ascending order by offset. This is to allow the transformation of the old archive into the delta-friendly space to be done by reading a the old archive as a stream, instead of requiring random access.
* Entries must not overlap (for sanity)
* Areas of the old archive that are not included in any uncompression op will be left alone, i.e. represent arbitrary data that should **not** be uncompressed, such as zip structural components or blocks of data that are stored without compression already.

```
|------------------------------------------------------|
| Offset of first byte to uncompress (8 bytes) (uint64)|
|------------------------------------------------------|
| Number of bytes to uncompress (8 bytes) (uint64)     |
|------------------------------------------------------|
```

## New Archive Recompression Op
The number of these entries is determined by the "Num new archive recompression ops" field previously defined. Like an old archive uncompression op, each entry consists of an offset - but this time from the beginning of the delta-friendly new blob. This is followed by the number of bytes to compress, and finally a compression settings field. Important notes:

* Entries must be ordered in ascending order by offset. This allows the output from the delta apply process (which creates the delta-friendly new blob) to be piped to an intelligent partially-compressing stream that is seeded with the knowledge of which ranges to recompress and the settings to use for each. This avoids the need to write the delta-friendly new blob to persistent storage, an important optimization.
* Entries must not overlap (for sanity)
* Areas of the new archive that are not included in any recompression op will be copied through from the delta-friendly new blob without modification. These represent arbitrary data that should **not** be compressed, such as zip structural components or blocks of data that are stored without compression in the new archive.

```
|------------------------------------------------------|
| Offset of first byte to compress (8 bytes) (uint64)  |
|------------------------------------------------------|
| Number of bytes to compress (8 bytes) (uint64)       |
|------------------------------------------------------|
| Compression settings (4 bytes)                       | (see definition below)
|------------------------------------------------------|
```

## Compression Settings
The compression settings define the deflate level (in the range 1 to 9, inclusive), the deflate strategy (in the range 0 to 2, inclusive) and the wrapping mode (wrap or nowrap). The settings are specific to a **compatibility window**, discussed in the next section in more detail.

> *In practice almost all entries in zip archives have strategy 0 (the default) and wrapping mode 'nowrap'. The other strategies are primarily used in-situ, e.g., the compression used within the PNG format; wrapping, on the other hand, is almost exclusively used in gzip operations.*

```
|------------------------------------------------------|
| Compatibility window ID (1 byte) (uint8)             | (see definition below)
|------------------------------------------------------|
| Deflate level (1 byte) (uint8) (range: [1,9])        |
|------------------------------------------------------|
| Deflate strategy (1 bytes) (uint8) (range: [0,2]     |
|------------------------------------------------------|
| Wrap mode (1 bytes) (uint8) (0=wrap, 1=nowrap)       |
|------------------------------------------------------|
```

## Compatibility Window
A compatibility window specifies a compression algorithm along with a range of versions and platforms upon which it is known to produce predictable and consistent output. That is, all implementations within a given compatibility window must produce *identical output* for any *identical inputs* consisting of bytes to be compressed along with the compression settings (level, strategy, wrapping mode).

In File-by-File v1, there is only one compatibility window defined. It is **the default deflate compatibility window**, having **ID=0** (all other values reserved for future expansion), and it specifies the following configuration:

* Algorithm: deflate (zlib)
* Window length: 32,768 bytes (hardcoded and implied, not explicitly set)
* Valid compression levels: 1 through 9 (0 means store, and is unused)
* Valid strategies: 0, 1, or 2 (java.util.zip does not support any later strategies)
* Valid wrapping modes: wrap, nowrap

The default compatibility window is compatible with the following runtime environments based on empirical testing. Other environments may be compatible, but the ones in this table are known to be.

Runtime Environment | OS | Hardware Architectures | Min Version | Max Version | Notes
--- | --- | --- | --- | --- | ---
Sun/Oracle JRE (including OpenJDK) | Linux | x64 | 1.7 (07 Jul, 2011) | None known as of September 2016 | Still compatible as of 1.8, the latest as of August 2016. Versions prior to 1.7 have different level_flags (see [zlib change](https://github.com/madler/zlib/commit/086e982175da84b3db958191031380794315f95f)).
Dalvik/ART | Android | armeabi­v7a, arm64­v8a, x86 | API 15 (19 Oct, 2011) | None known as of September 2016 | Still compatible as of API 24 (Nougat), the latest as of September 2016. Versions prior to API 15 (Ice Cream Sandwich) used a smaller sliding window size (see [AOSP change](https://android.googlesource.com/platform/libcore/+/909a18fd6628cee6718865a7b7bf2534ea25f5ec%5E%21/#F0)).

## Delta Descriptor Record
Delta descriptor records are grouped together before any of the actual deltas. In File-by-File v1 there is always exactly one delta, so there is exactly one delta descriptor record followed immediately by the delta data. Conceptually, the descriptor defines input and output regions of the archives along with a delta to be applied to those regions (reading from one, and writing to the other).

> *In subsequent versions there may be arbitrarily many deltas. When there is more than one delta, all the descriptors are listed in a contiguous block followed by all of the deltas themselves, also in a contiguous block. This allows the patch applier to pre­process the list of all deltas that are going to be applied and allocate resources accordingly. As with the other descriptors, these must be ordered by ascending offset and overlaps are not allowed.*

```
|------------------------------------------------------|
| Delta format ID (1 byte) (uint8)                     |
|------------------------------------------------------|
| Old delta-friendly region start (8 bytes) (uint64)   |
|------------------------------------------------------|
| Old delta-friendly region length (8 bytes) (uint64)  |
|------------------------------------------------------|
| New delta-friendly region start (8 bytes) (uint64)   |
|------------------------------------------------------|
| New delta-friendly region length (8 bytes) (uint64)  |
|------------------------------------------------------|
| Delta length (8 bytes) (uint64)                      |
|------------------------------------------------------|
```

Description of the fields within this record are a little more complex than in the other parts of the patch:

* **Delta format**: The only delta format in File-by-File v1 is **bsdiff**, having **ID=0**.
* **Old delta-friendly region start**: The offset into the old archive (*after* transformation *into* the delta-friendly space) to which the delta applies. In File-by-File v1, this is always zero.
* **Old delta-friendly region length**: The number of bytes in the old archive (again, *after* transformation *into* the delta-friendly space) to which the delta applies. In File-by-File v1, this is always the length of the old archive in the delta-friendly space.
* **New delta-friendly region start**: The offset into the new archive (*before* transformation *out of* the delta-friendly space) to which the delta applies. In File-by-File v1, this is always zero.
* **New delta-friendly region length**: The number of bytes in the new archive (again, *before* transformation *out of* the delta-friendly space) to which the delta applies. In File-by-File v1, this is always the length of the new archive in the delta-friendly space.
* **Delta length**: The number of bytes in the actual delta (e.g., a bsdiff patch) that needs to be applied to the regions defined above. The type of the delta is determined by the delta format, also defined above.

# Appendix

## Interesting Obstacles to Patching Archives

### Problem #1: Spatial Efficiency
**Problem**: Zip files make patching hard because compression obscures the changes. Deflate, the compression algorithm used most widely in zip archives, uses a 32k "sliding window" to compress, carrying state with it as it goes. Because state is carried along, even small changes to the data that is being compressed can result in drastic changes to the bytes that are output - even if the size remains similar. If you change the definition of 'aardvark' in our imaginary dictionary (from back in the [Background](#background) section) and zip both the old and new copies, the resulting zip files will be about the **same size**, but will have very **different bytes**. If you try to generate a patch between the two zip files with the same algorithm you used before (e.g., bsdiff) you'll find that the resulting patch file is much, much larger - probably about the same size of one of the zip files. This is because the files are too dissimilar to express any changes succinctly, so the patching algorithm ends up having to just embed a copy of almost the entire file.

**Solution**: Archive-patcher **transforms** the input archives into what we refer to as **delta-friendly space** where changed files are stored uncompressed, allowing diffing algorithms like bsdiff to function far more effectively.

> *Note: There are techniques that can be applied to deflate to isolate changes and stop them from causing the entire output to be different, such those used in rsync-friendly gzip. However, zip archives created with such techniques are uncommon - and tend to be slightly larger in size.*

### Problem #2: Correctness When Generating Patches
**Problem**: In order for the generated patch to be correct, we need to know the **original deflate settings** that were used for any changed content that we plan to uncompress during the transformation to the delta-friendly space. This is necessary so that the patch applier can **recompress** that changed content after applying the delta, such that the resulting archive is exactly the same as the input to the patch generator. The deflate settings we care about are the **level**, **strategy**, and **wrap mode**.

**Solution**: Archive-patcher iteratively recompresses each piece of changed content with different deflate settings, looking for a perfect match. The search is ordered based on empirical data and one of the first 3 guesses is extremely likely to succeed. Because deflate has a stateful and small sliding window, mismatches are quickly identified and discarded. If a match *is* found, the corresponding settings are added to the patch stream and the content is uncompressed in-place as previously described; if a match *is not* found then the content is left compressed (because we lack any way to tell the patch applier how to recompress it later).

> *Note: While it is possible to change other settings for deflate (like the size of its sliding window), in practice this is almost never done. Content that has been compressed with other settings changed will be left compressed during patch generation.*

### Problem #3: Correctness When Applying Patches
**Problem**: The patch applier needs to know that it can reproduce deflate output in exactly the same way as the patch generator did. If this is not possible, patching will fail. The biggest risk is that the patch applier's implementation of deflate differs in some way from that of the patch generator that detected the deflate settings. Any deviation will cause the output to diverge from the original input to the patch generator. Archive-patcher relies on the java.util.zip package which in turn wraps a copy of zlib that ships with the JRE. It is this version of zlib that provides the implementation of deflate.

**Solution**: Archive-patcher contains a ~9000 byte **corpus** of text that produces a unique output for every possible combination of deflate settings that are exposed through the java.util.zip interface (level, strategy, and wrapping mode). These outputs are digested to produce "fingerprints" for each combination of deflate settings on a given version of the zlib library; these fingerprints are then hard-coded into the application. The patch applier checks the local zlib implementation's suitability by repeating the process, deflating the corpus with each combination of java.util.zip settings and digesting the results, then checks that the resulting fingerprints match the hard-coded values.

> *Note: At the time of this writing (September, 2016), all zlib versions since 1.2.0.4 (dated 10 August 2003) have identical fingerprints. This includes every version of Sun/Oracle Java from 1.6.0 onwards on x86 and x86_64 as well as all versions of the Android Open Source Project from 4.0 onward on x86, arm32 and arm64. Other platforms may also be compatible but have not been tested.*

> *Note: This solution is somewhat brittle, but is demonstrably suitable to cover 13 years of zlib updates. Compatibility may be extended in a future version by bundling specific versions of zlib with the application to avoid a dependency upon the zlib in the JRE as necessary.*

## Areas For Improvement
The File-by-File v1 patching process dramatically improves the spatial efficiency of patches for zip archives, but there are many improvements that can still be made. Here are a few of the more obvious ones that did not make it into v1, but are good candidates for inclusion into later versions:

* Support for detecting "similar" files between the old and new archives to handle renames that are coupled with content changes.
* Support for additional versions of zlib or other implementations of deflate.
* Support for other archive formats.
* Support for other delta algorithms.
* Support for more than one delta (i.e., applying different algorithms to different regions of the archives).
* Support for file-specific transformations (i.e., delta-friendly optimization of different files types during the transformation into the delta-friendly space).
* Support for partial decompression (i.e., only undoing the Huffman coding steps of deflate and operating on the LZ77 instruction stream during patch generation, allowing a much faster "recompression" step that skips LZ77).

# Acknowledgements
Major software contributions, in alphabetical order:

* [Andrew Hayden](mailto:andrew.hayden@gmail.com) - design, implementation, documentation
* Anthony Morris - code reviews, cleanups, div suffix sort port, and invaluable suggestions
* Glenn Hartmann - code reviews, initial bsdiff port and quick suffix sort port, bsdiff cleanups

Additionally, we wish to acknowledge the following, also in alphabetical order:

* Colin Percival - the author of [bsdiff](http://www.daemonology.net/bsdiff/)
* Mark Adler - the author of [zlib](http://www.zlib.net)
* N. Jesper Larsson and Kunihiko Sadakane - for their paper "[Faster Suffix Sorting](http://www.larsson.dogma.net/ssrev-tr.pdf)", basis of the quick suffix sort algorithm
* PKWARE, Inc. - creators and stewards of the [zip specification](https://support.pkware.com/display/PKZIP/APPNOTE)
* Yuta Mori - for the C implementation of the "div suffix sort" ([libdivsufsort](http://code.google.com/p/libdivsufsort/))
