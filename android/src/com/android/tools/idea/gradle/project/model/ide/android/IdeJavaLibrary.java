/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.model.ide.android;

import com.android.builder.model.JavaLibrary;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Creates a deep copy of a {@link JavaLibrary}.
 */
public final class IdeJavaLibrary extends IdeLibrary implements JavaLibrary, Serializable {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 1L;

  @NotNull private final File myJarFile;
  @NotNull private final List<JavaLibrary> myDependencies;

  public IdeJavaLibrary(@NotNull JavaLibrary library, @NotNull ModelCache modelCache) {
    super(library);

    myJarFile = library.getJarFile();

    List<? extends JavaLibrary> dependencies = library.getDependencies();
    myDependencies = new ArrayList<>(dependencies.size());
    for (JavaLibrary dependency : dependencies) {
      IdeJavaLibrary copy = modelCache.computeIfAbsent(dependency, key -> new IdeJavaLibrary(dependency, modelCache));
      myDependencies.add(copy);
    }
  }

  @Override
  @NotNull
  public File getJarFile() {
    return myJarFile;
  }

  @Override
  @NotNull
  public List<? extends JavaLibrary> getDependencies() {
    return myDependencies;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof IdeJavaLibrary)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    IdeJavaLibrary library = (IdeJavaLibrary)o;
    return library.canEqual(this) &&
           Objects.equals(myJarFile, library.myJarFile) &&
           Objects.equals(myDependencies, library.myDependencies);
  }

  @Override
  public boolean canEqual(Object other) {
    return other instanceof IdeJavaLibrary;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), myJarFile, myDependencies);
  }

  @Override
  public String toString() {
    return "IdeJavaLibrary{" +
           super.toString() +
           ", myJarFile=" + myJarFile +
           ", myDependencies=" + myDependencies +
           "}";
  }
}
