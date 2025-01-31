// Copyright 2016 Google LLC. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.archivepatcher.generator;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Convenience methods for testing with {@link PreDiffPlanEntry}. */
public class PreDiffPlanEntryTestUtils {
  /**
   * Returns a {@link PreDiffPlanEntry.Builder} with compression settings corresponding to {@link
   * UncompressionOptionExplanation#COMPRESSED_CHANGED_TO_UNCOMPRESSED}
   */
  public static PreDiffPlanEntry.Builder builderWithCompressedToUncompressed() {
    return PreDiffPlanEntry.builder()
        .setUncompressionOption(
            ZipEntryUncompressionOption.UNCOMPRESS_OLD,
            UncompressionOptionExplanation.COMPRESSED_CHANGED_TO_UNCOMPRESSED);
  }

  /**
   * Returns a {@link PreDiffPlanEntry.Builder} with compression settings corresponding to {@link
   * UncompressionOptionExplanation#UNCOMPRESSED_CHANGED_TO_COMPRESSED}
   */
  public static PreDiffPlanEntry.Builder builderWithUncompressedToCompressed() {
    return PreDiffPlanEntry.builder()
        .setUncompressionOption(
            ZipEntryUncompressionOption.UNCOMPRESS_NEW,
            UncompressionOptionExplanation.UNCOMPRESSED_CHANGED_TO_COMPRESSED);
  }

  /**
   * Returns a {@link PreDiffPlanEntry.Builder} with compression settings corresponding to {@link
   * UncompressionOptionExplanation#COMPRESSED_BYTES_IDENTICAL}
   */
  public static PreDiffPlanEntry.Builder builderWithCompressedBytesIdentical() {
    return PreDiffPlanEntry.builder()
        .setUncompressionOption(
            ZipEntryUncompressionOption.UNCOMPRESS_NEITHER,
            UncompressionOptionExplanation.COMPRESSED_BYTES_IDENTICAL);
  }

  /**
   * Returns a {@link PreDiffPlanEntry.Builder} with compression settings corresponding to {@link
   * UncompressionOptionExplanation#BOTH_ENTRIES_UNCOMPRESSED}
   */
  public static PreDiffPlanEntry.Builder builderWithBothEntriesUncompressed() {
    return PreDiffPlanEntry.builder()
        .setUncompressionOption(
            ZipEntryUncompressionOption.UNCOMPRESS_NEITHER,
            UncompressionOptionExplanation.BOTH_ENTRIES_UNCOMPRESSED);
  }

  /**
   * Returns a {@link PreDiffPlanEntry.Builder} with compression settings corresponding to {@link
   * UncompressionOptionExplanation#UNSUITABLE}
   */
  public static PreDiffPlanEntry.Builder builderWithUnsuitable() {
    return PreDiffPlanEntry.builder()
        .setUncompressionOption(
            ZipEntryUncompressionOption.UNCOMPRESS_NEITHER,
            UncompressionOptionExplanation.UNSUITABLE);
  }

  /**
   * Returns a {@link PreDiffPlanEntry.Builder} with compression settings corresponding to {@link
   * UncompressionOptionExplanation#DEFLATE_UNSUITABLE}
   */
  public static PreDiffPlanEntry.Builder builderWithDeflateUnsuitable() {
    return PreDiffPlanEntry.builder()
        .setUncompressionOption(
            ZipEntryUncompressionOption.UNCOMPRESS_NEITHER,
            UncompressionOptionExplanation.DEFLATE_UNSUITABLE);
  }

  /**
   * Returns a {@link PreDiffPlanEntry.Builder} with compression settings corresponding to {@link
   * UncompressionOptionExplanation#COMPRESSED_BYTES_CHANGED}
   */
  public static PreDiffPlanEntry.Builder builderWithCompressedBytesChanged() {
    return PreDiffPlanEntry.builder()
        .setUncompressionOption(
            ZipEntryUncompressionOption.UNCOMPRESS_BOTH,
            UncompressionOptionExplanation.COMPRESSED_BYTES_CHANGED);
  }

  /**
   * Given {@link PreDiffPlanEntry}s, manufacture equivalents altered in the way that the {@link
   * DeltaFriendlyOldBlobSizeLimiter} would.
   *
   * @param originals the original entries
   * @return the altered entries
   */
  public static final List<PreDiffPlanEntry> suppressed(PreDiffPlanEntry... originals) {
    return Arrays.stream(originals)
        .map(
            originalEntry ->
                originalEntry.toBuilder()
                    .setUncompressionOption(
                        ZipEntryUncompressionOption.UNCOMPRESS_NEITHER,
                        UncompressionOptionExplanation.RESOURCE_CONSTRAINED)
                    .build())
        .collect(Collectors.toList());
  }
}
