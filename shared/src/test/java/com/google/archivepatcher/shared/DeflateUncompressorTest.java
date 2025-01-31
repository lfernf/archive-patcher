// Copyright 2015 Google LLC. All rights reserved.
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

package com.google.archivepatcher.shared;

import static com.google.common.truth.Truth.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link com.google.archivepatcher.shared.DeflateUncompressor}. */
@RunWith(JUnit4.class)
@SuppressWarnings("javadoc")
public class DeflateUncompressorTest {

  /**
   * Test data for compression. Uses the {@link DefaultDeflateCompatibilityWindow}'s corpus because
   * it is already set up to produce different outputs for each compression level.
   */
  private final static byte[] CONTENT = new DefaultDeflateCompatibilityWindow().getCorpus();

  private byte[] compressedContent;
  private ByteArrayInputStream compressedContentIn;
  private DeflateUncompressor uncompressor;
  private ByteArrayOutputStream uncompressedContentOut;

  @Before
  public void setUp() throws IOException {
    ByteArrayOutputStream compressedContentBuffer = new ByteArrayOutputStream();
    Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
    DeflaterOutputStream deflateOut = new DeflaterOutputStream(compressedContentBuffer, deflater);
    deflateOut.write(CONTENT);
    deflateOut.finish();
    deflateOut.close();
    deflater.end();
    compressedContent = compressedContentBuffer.toByteArray();
    uncompressor = new DeflateUncompressor();
    compressedContentIn = new ByteArrayInputStream(compressedContent);
    uncompressedContentOut = new ByteArrayOutputStream();
  }

  @Test
  public void testUncompress() throws IOException {
    uncompressor.uncompress(compressedContentIn, uncompressedContentOut);
    assertThat(uncompressedContentOut.toByteArray()).isEqualTo(CONTENT);
  }

  @Test
  public void testCorrectDefaults() {
    // Sanity check to ensure that defaults are as we want them to be. Arguably crufty but nobody
    // should change these without some thought, particularly the wrapping choice should not be
    // changed in the compressor without also changing it in the *un*compressor.
    assertThat(uncompressor.isNowrap()).isTrue();
  }

  @Test
  public void testNowrap() throws IOException {
    // Recompress with nowrap set to false.
    Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, false /* nowrap */);
    ByteArrayOutputStream compressedContentBuffer = new ByteArrayOutputStream();
    DeflaterOutputStream deflateOut = new DeflaterOutputStream(compressedContentBuffer, deflater);
    deflateOut.write(CONTENT);
    deflateOut.finish();
    deflateOut.close();
    deflater.end();
    compressedContent = compressedContentBuffer.toByteArray();
    compressedContentIn = new ByteArrayInputStream(compressedContent);

    // Now expect wrapped content in the uncompressor, and uncompressing should "just work".
    uncompressor.setNowrap(false);
    uncompressor.uncompress(compressedContentIn, uncompressedContentOut);
    assertThat(uncompressedContentOut.toByteArray()).isEqualTo(CONTENT);
  }

  @Test
  public void testSetInputBufferSize() throws IOException {
    assertThat(uncompressor.getInputBufferSize()).isNotEqualTo(17); // Ensure test is valid
    uncompressor.setInputBufferSize(17); // Arbitrary non-default value
    assertThat(uncompressor.getInputBufferSize()).isEqualTo(17);
    uncompressor.uncompress(compressedContentIn, uncompressedContentOut);
    assertThat(uncompressedContentOut.toByteArray()).isEqualTo(CONTENT);
  }

  @Test
  public void testSetOutputBufferSize() throws IOException {
    assertThat(uncompressor.getOutputBufferSize()).isNotEqualTo(17); // Ensure test is valid
    uncompressor.setOutputBufferSize(17); // Arbitrary non-default value
    assertThat(uncompressor.getOutputBufferSize()).isEqualTo(17);
    uncompressor.uncompress(compressedContentIn, uncompressedContentOut);
    assertThat(uncompressedContentOut.toByteArray()).isEqualTo(CONTENT);
  }

  @Test
  public void testCreateOrResetInflater_Uncached() {
    uncompressor.setCaching(false);
    Inflater inflater1 = uncompressor.createOrResetInflater();
    Inflater inflater2 = uncompressor.createOrResetInflater();
    assertThat(inflater1).isNotSameAs(inflater2);
  }

  @Test
  public void testCreateOrResetInflater_Cached() {
    uncompressor.setCaching(true);
    Inflater inflater1 = uncompressor.createOrResetInflater();
    Inflater inflater2 = uncompressor.createOrResetInflater();
    assertThat(inflater1).isSameAs(inflater2);
  }

  @Test
  public void testRelease() {
    uncompressor.setCaching(true);
    Inflater inflater1 = uncompressor.createOrResetInflater();
    uncompressor.release();
    Inflater inflater2 = uncompressor.createOrResetInflater();
    assertThat(inflater1).isNotSameAs(inflater2);
  }

  @Test
  public void testReusability() throws IOException {
    // Checks that the uncompressor produces correct output when cached, i.e. that it is being
    // properly reset between runs.
    uncompressor.setCaching(true);
    uncompressor.uncompress(compressedContentIn, uncompressedContentOut);
    assertThat(uncompressedContentOut.toByteArray()).isEqualTo(CONTENT);

    // Caching is on, try to reuse it without any changes.
    compressedContentIn = new ByteArrayInputStream(compressedContent);
    uncompressedContentOut = new ByteArrayOutputStream();
    uncompressor.uncompress(compressedContentIn, uncompressedContentOut);
    assertThat(uncompressedContentOut.toByteArray()).isEqualTo(CONTENT);

    // Caching is still on, reverse the wrapping style and try again. Changing the wrapping style
    // invalidates the cached uncompressor because the wrapping style cannot be changed, this is a
    // special code path we need to exercise.
    Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, false /* nowrap */);
    ByteArrayOutputStream compressedContentBuffer = new ByteArrayOutputStream();
    DeflaterOutputStream deflateOut = new DeflaterOutputStream(compressedContentBuffer, deflater);
    deflateOut.write(CONTENT);
    deflateOut.finish();
    deflateOut.close();
    deflater.end();
    compressedContent = compressedContentBuffer.toByteArray();
    compressedContentIn = new ByteArrayInputStream(compressedContent);

    // Now expect wrapped content in the uncompressor, and uncompressing should "just work".
    uncompressor.setNowrap(false);
    uncompressedContentOut = new ByteArrayOutputStream();
    uncompressor.uncompress(compressedContentIn, uncompressedContentOut);
    assertThat(uncompressedContentOut.toByteArray()).isEqualTo(CONTENT);
  }
}
