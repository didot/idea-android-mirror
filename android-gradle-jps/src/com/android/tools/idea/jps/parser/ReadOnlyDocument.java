/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.jps.parser;

import com.google.common.collect.Lists;
import com.google.common.io.Closeables;
import com.intellij.util.text.StringSearcher;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.List;

/**
 * A read-only representation of the text of a file.
 */
class ReadOnlyDocument {
  @NotNull private final CharSequence myContents;
  @NotNull private final List<Long> myOffsets;

  /**
   * Creates a new {@link ReadOnlyDocument} for the given file.
   *
   * @param file the file whose text will be stored in the document. UTF-8 charset is used to decode
   *             the contents of the file.
   * @throws java.io.IOException if an error occurs while reading the file.
   */
  ReadOnlyDocument(@NotNull File file) throws IOException {
    myOffsets = Lists.newArrayList();
    RandomAccessFile raf = null;
    try {
      raf = new RandomAccessFile(file, "r");
      myOffsets.add(raf.getFilePointer());
      while (raf.readLine() != null) {
        myOffsets.add(raf.getFilePointer());
      }
      FileChannel channel = raf.getChannel();
      long channelSize = channel.size();
      ByteBuffer byteBuffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channelSize);
      myContents = Charset.forName("UTF-8").newDecoder().decode(byteBuffer);
    }
    finally {
      Closeables.closeQuietly(raf);
    }
  }

  /**
   * Returns the offset of the given line number, relative to the beginning of the document.
   *
   * @param lineNumber the given line number.
   * @return the offset of the given line. -1 is returned if the document is empty, or if the given
   *         line number is negative or greater than the number of lines in the document.
   */
  long lineOffset(long lineNumber) {
    int index = (int)lineNumber - 1;
    if (index <= 0 || index >= myOffsets.size()) {
      return -1L;
    }
    return myOffsets.get(index);
  }

  /**
   * Returns the line number of the given offset.
   *
   * @param offset the given offset.
   * @return the line number of the given offset. -1 is returned if the document is empty or if the offset is greater than the position of
   *         the last character in the document.
   */
  long lineNumber(long offset) {
    for (int i = 0; i < myOffsets.size(); i++) {
      long savedOffset = myOffsets.get(i);
      if (offset <= savedOffset) {
        return i;
      }
    }
    return -1L;
  }

  /**
   * Finds the given text in the document, starting from the given offset.
   *
   * @param text   the text to find.
   * @param offset the starting point of the search.
   * @return the offset of the found result, or -1 if no match was found.
   */
  long findText(String text, long offset) {
    StringSearcher searcher = new StringSearcher(text, true, true);
    return searcher.scan(myContents, (int)offset, myContents.length());
  }

  /**
   * Returns the character at the given offset.
   *
   * @param offset the position, relative to the beginning of the document, of the character to return.
   * @return the character at the given offset.
   * @throws IndexOutOfBoundsException if the {@code offset} argument is negative or not less than the document's size.
   */
  char getCharAt(long offset) {
    return myContents.charAt((int)offset);
  }

  /**
   * @return the size (or length) of the document.
   */
  long length() {
    return myContents.length();
  }
}
