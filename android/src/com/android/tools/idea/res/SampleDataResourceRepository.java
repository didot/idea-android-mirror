/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.res;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.res2.ResourceTable;
import com.android.resources.ResourceType;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static com.android.SdkConstants.FD_SAMPLE_DATA;


public class SampleDataResourceRepository extends LocalResourceRepository {
  private final ResourceTable myFullTable;

  @Nullable
  public static VirtualFile getSampleDataDir(@NotNull AndroidFacet androidFacet, boolean create) throws IOException {
    VirtualFile contentRoot = AndroidRootUtil.getMainContentRoot(androidFacet);
    if (contentRoot == null) {
      throw new IOException("Unable to find content root");
    }

    VirtualFile sampleDataDir = contentRoot.findFileByRelativePath("/" + FD_SAMPLE_DATA);
    if (sampleDataDir == null && create) {
        sampleDataDir = WriteCommandAction.runWriteCommandAction(androidFacet.getModule().getProject(),
                                                                 (ThrowableComputable<VirtualFile, IOException>)() -> contentRoot.createChildDirectory(androidFacet, FD_SAMPLE_DATA));
    }

    return sampleDataDir;
  }

  protected SampleDataResourceRepository(AndroidFacet androidFacet) {
    super("MockData");

    myFullTable = new ResourceTable();

    // Find all the files in the mocks directory
    VirtualFile contentRoot = AndroidRootUtil.getMainContentRoot(androidFacet);
    VirtualFile sampleDataDir = null;
    try {
      sampleDataDir = getSampleDataDir(androidFacet, false);
    }
    catch (IOException ignore) {
    }

    if (sampleDataDir != null) {
      ImmutableListMultimap.Builder<String, ResourceItem> items = ImmutableListMultimap.builder();
      PsiManager psiManager = PsiManager.getInstance(androidFacet.getModule().getProject());
      Stream<VirtualFile> childrenStream = Arrays.stream(sampleDataDir.getChildren());
      ApplicationManager.getApplication().runReadAction(() -> {
        childrenStream
          .map(psiManager::findFile)
          .filter(Objects::nonNull)
          .forEach(f -> items.put(f.getName(),
                                  new PsiResourceItem(f.getName(), ResourceType.SAMPLE_DATA, null, null, f)));
      });

      myFullTable.put(null, ResourceType.SAMPLE_DATA, items.build());
    }
  }

  @NonNull
  @Override
  protected ResourceTable getFullTable() {
    return myFullTable;
  }

  @Nullable
  @Override
  protected ListMultimap<String, ResourceItem> getMap(@Nullable String namespace, @NonNull ResourceType type, boolean create) {
    return myFullTable.get(namespace, type);
  }

  @NonNull
  @Override
  public Set<String> getNamespaces() {
    return Collections.emptySet();
  }

  @NotNull
  @Override
  protected Set<VirtualFile> computeResourceDirs() {
    return ImmutableSet.of();
  }
}
