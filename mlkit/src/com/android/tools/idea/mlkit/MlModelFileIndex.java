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
package com.android.tools.idea.mlkit;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.mlkit.MlConstants;
import com.android.tools.mlkit.MlkitNames;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexExtension;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.ID;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Indexes machine learning model (e.g. TFLite model) files under the ml folder.
 */
public class MlModelFileIndex extends FileBasedIndexExtension<String, MlModelMetadata> {
  public static final ID<String, MlModelMetadata> INDEX_ID = ID.create("MlModelFileIndex");

  @NotNull
  @Override
  public DataIndexer<String, MlModelMetadata, FileContent> getIndexer() {
    return new DataIndexer<String, MlModelMetadata, FileContent>() {
      @NotNull
      @Override
      public Map<String, MlModelMetadata> map(@NotNull FileContent inputData) {
        VirtualFile modelFile = inputData.getFile();
        String className = MlkitNames.computeModelClassName(VfsUtilCore.virtualToIoFile(modelFile));
        MlModelMetadata modelMetadata = new MlModelMetadata(modelFile.getUrl(), className);
        return ImmutableMap.of(className, modelMetadata);
      }
    };
  }

  @NotNull
  @Override
  public DataExternalizer<MlModelMetadata> getValueExternalizer() {
    return new DataExternalizer<MlModelMetadata>() {
      @Override
      public void save(@NotNull DataOutput out, MlModelMetadata value) throws IOException {
        if (value != null) {
          out.writeUTF(value.myModelFileUrl);
          out.writeUTF(value.myClassName);
        }
      }

      @Override
      public MlModelMetadata read(@NotNull DataInput in) throws IOException {
        String fileUrl = in.readUTF();
        String className = in.readUTF();
        return new MlModelMetadata(fileUrl, className);
      }
    };
  }

  @NotNull
  @Override
  public KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @Override
  public int getVersion() {
    return 1;
  }

  @NotNull
  @Override
  public ID<String, MlModelMetadata> getName() {
    return INDEX_ID;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return file -> StudioFlags.ML_MODEL_BINDING.get() &&
                   file.getFileType() == TfliteModelFileType.INSTANCE &&
                   file.getLength() <= MlConstants.MAX_SUPPORTED_MODEL_FILE_SIZE_IN_BYTES;
  }

  @Override
  public boolean dependsOnFileContent() {
    // We don't actually index the model file content, just some metadata instead. So return false here to bypass the file size check used
    // by the content loading, which is the precondition for indexing large model file (> 20 MB).
    return false;
  }

  @NotNull
  @Override
  public Collection<FileType> getFileTypesWithSizeLimitNotApplicable() {
    return Collections.singletonList(TfliteModelFileType.INSTANCE);
  }
}
