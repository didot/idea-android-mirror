/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.room.bundle;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * This was copied from the Room Migration project. It is only a temporary solution and in the future we will try to use the real classes.
 */
public class FtsOptionsBundle implements SchemaEquality<FtsOptionsBundle> {
  @SerializedName("tokenizer")
  private final String mTokenizer;
  @SerializedName("tokenizerArgs")
  private final List<String> mTokenizerArgs;
  @SerializedName("contentTable")
  private final String mContentTable;
  @SerializedName("languageIdColumnName")
  private final String mLanguageIdColumnName;
  @SerializedName("matchInfo")
  private final String mMatchInfo;
  @SerializedName("notIndexedColumns")
  private final List<String> mNotIndexedColumns;
  @SerializedName("prefixSizes")
  private final List<Integer> mPrefixSizes;
  @SerializedName("preferredOrder")
  private final String mPreferredOrder;
  public FtsOptionsBundle(
    String tokenizer,
    List<String> tokenizerArgs,
    String contentTable,
    String languageIdColumnName,
    String matchInfo,
    List<String> notIndexedColumns,
    List<Integer> prefixSizes,
    String preferredOrder) {
    mTokenizer = tokenizer;
    mTokenizerArgs = tokenizerArgs;
    mContentTable = contentTable;
    mLanguageIdColumnName = languageIdColumnName;
    mMatchInfo = matchInfo;
    mNotIndexedColumns = notIndexedColumns;
    mPrefixSizes = prefixSizes;
    mPreferredOrder = preferredOrder;
  }
  /**
   * @return The external content table name
   */
  public String getContentTable() {
    return mContentTable;
  }
  @Override
  public boolean isSchemaEqual(FtsOptionsBundle other) {
    return mTokenizer.equals(other.mTokenizer)
           && mTokenizerArgs.equals(other.mTokenizerArgs)
           && mContentTable.equals(other.mContentTable)
           && mLanguageIdColumnName.equals(other.mLanguageIdColumnName)
           && mMatchInfo.equals(other.mMatchInfo)
           && mNotIndexedColumns.equals(other.mNotIndexedColumns)
           && mPrefixSizes.equals(other.mPrefixSizes)
           && mPreferredOrder.equals(other.mPreferredOrder);
  }
}
