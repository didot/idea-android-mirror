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

import com.android.ide.common.rendering.api.AssetRepository;
import com.android.tools.idea.fonts.DownloadableFontCacheService;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Finds an asset in all the asset directories and returns the input stream.
 */
public class AssetRepositoryImpl extends AssetRepository implements Disposable {

  private AndroidFacet myFacet;

  public AssetRepositoryImpl(@NotNull AndroidFacet facet) {
    myFacet = facet;

    // LayoutLib keeps a static reference to the AssetRepository that will be replaced once a new project is opened.
    // In unit tests this will trigger a memory leak error. This makes sure that we do not keep the reference to the facet so
    // the unit test is happy.
    Disposer.register(myFacet, this);
  }

  @Override
  public boolean isSupported() {
    return true;
  }

  /**
   * @param mode one of ACCESS_UNKNOWN, ACCESS_STREAMING, ACCESS_RANDOM or ACCESS_BUFFER (int values 0-3).
   */
  @Nullable
  @Override
  public InputStream openAsset(@NotNull String path, int mode) throws IOException {
    assert myFacet != null;

    List<IdeaSourceProvider> sourceProviders = getAllAndroidSourceProviders();
    for (int i = sourceProviders.size() - 1; i >= 0; i--) {
      for (VirtualFile assetDir : sourceProviders.get(i).getAssetsDirectories()) {
        VirtualFile asset = assetDir.findFileByRelativePath(path);
        if (asset != null) {
          // TODO: ensure that this asset is within the asset directory. Files outside the asset directory shouldn't be accessible.
          return asset.getInputStream();
        }
      }
    }

    return null;
  }

  /**
   * Returns whether the given file is contained within the downloadable fonts cache
   */
  private static boolean isCachedFontFile(@NotNull VirtualFile file) {
    File fontCachePathFile = DownloadableFontCacheService.getInstance().getFontPath();
    if (fontCachePathFile == null) {
      return false;
    }

    VirtualFile fontCachePath = VirtualFileManager.getInstance().findFileByUrl("file://" + fontCachePathFile.getAbsolutePath());
    if (fontCachePath == null) {
      return false;
    }
    return VfsUtilCore.isAncestor(fontCachePath, file, true);
  }

  /**
   * It takes an absolute path that does not point to an asset and opens the file. Currently the access is restricted to files under
   * the resources directories and the downloadable font cache directory.
   * @param mode one of ACCESS_UNKNOWN, ACCESS_STREAMING, ACCESS_RANDOM or ACCESS_BUFFER (int values 0-3).
   */
  @Nullable
  @Override
  public InputStream openNonAsset(int cookie, @NotNull String path, int mode) throws IOException {
    assert myFacet != null;

    VirtualFile file = VirtualFileManager.getInstance().findFileByUrl("file://" + path);
    if (file == null) {
      return null;
    }

    List<IdeaSourceProvider> sourceProviders = getAllAndroidSourceProviders();
    for (int i = sourceProviders.size() - 1; i >= 0; i--) {
      // Verify that the file at least contained in one of the resource directories
      for (VirtualFile resDir : sourceProviders.get(i).getResDirectories()) {
        if (VfsUtilCore.isAncestor(resDir, file, true)) {
          return file.getInputStream();
        }
      }
    }

    if (isCachedFontFile(file)) {
      return file.getInputStream();
    }

    return null;
  }

  /**
   * Get all SourceProviders of current and dependent Android modules.
   * It is sorted according to the dependency path(s).
   * The most far one in dependency path is at the first of list, and the current facet one is at the end.
   *
   * @return The SourceProviders of Android module relate to {@link #myFacet}
   */
  private List<IdeaSourceProvider> getAllAndroidSourceProviders() {
    // mode is currently ignored. It can help in optimizing read performance, but it shouldn't matter here.

    return Stream.concat(AndroidUtils.getAllAndroidDependencies(myFacet.getModule(), true).stream(), Stream.of(myFacet))
      .flatMap(facet -> IdeaSourceProvider.getAllIdeaSourceProviders(facet).stream())
      .distinct()
      .collect(Collectors.toList());
  }

  @Override
  public void dispose() {
    myFacet = null;
  }
}
