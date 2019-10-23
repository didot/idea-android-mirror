/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.resources.aar;

import com.android.ide.common.rendering.api.AttrResourceValue;
import com.android.ide.common.rendering.api.AttributeFormat;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.resources.ResourceType;
import com.android.resources.ResourceVisibility;
import com.android.utils.HashCodes;
import com.intellij.util.containers.ObjectIntHashMap;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resource value representing a reference to an attr resource, but potentially with its own description
 * and group name. Unlike {@link AarAttrResourceItem}, does not contain formats and enum or flag information.
 */
class AarAttrReference extends AbstractAarValueResourceItem implements AttrResourceValue {
  @NotNull private final ResourceNamespace myNamespace;
  @Nullable private final String myDescription;
  @Nullable private final String myGroupName;

  /**
   * Initializes the attr reference.
   *
   * @param namespace the namespace of the attr resource
   * @param name the name of the resource
   * @param sourceFile the source file containing definition of the resource
   * @param description the description of the attr resource, if available
   * @param groupName the name of the attr group, if available
   */
  public AarAttrReference(@NotNull ResourceNamespace namespace,
                          @NotNull String name,
                          @NotNull AarSourceFile sourceFile,
                          @NotNull ResourceVisibility visibility,
                          @Nullable String description,
                          @Nullable String groupName) {
    super(ResourceType.ATTR, name, sourceFile, visibility);
    myNamespace = namespace;
    myDescription = description;
    myGroupName = groupName;
  }

  @Override
  @NotNull
  public ResourceNamespace getNamespace() {
    return myNamespace;
  }

  @Override
  @NotNull
  public final Set<AttributeFormat> getFormats() {
    return Collections.emptySet();
  }

  @Override
  @NotNull
  public final Map<String, Integer> getAttributeValues() {
    return Collections.emptyMap();
  }

  @Override
  @Nullable
  public final String getValueDescription(@NotNull String valueName) {
    return null;
  }

  @Override
  @Nullable
  public final String getDescription() {
    return myDescription;
  }

  @Override
  @Nullable
  public final String getGroupName() {
    return myGroupName;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    AarAttrReference other = (AarAttrReference) obj;
    return myNamespace.equals(other.myNamespace) &&
        Objects.equals(myDescription, other.myDescription) &&
        Objects.equals(myGroupName, other.myGroupName);
  }

  @Override
  public int hashCode() {
    // myGroupName is not included in hash code intentionally since it doesn't improve quality of hashing.
    return HashCodes.mix(super.hashCode(), myNamespace.hashCode(), Objects.hashCode(myDescription));
  }

  @Override
  void serialize(@NotNull Base128OutputStream stream,
                 @NotNull ObjectIntHashMap<String> configIndexes,
                 @NotNull ObjectIntHashMap<AarSourceFile> sourceFileIndexes,
                 @NotNull ObjectIntHashMap<ResourceNamespace.Resolver> namespaceResolverIndexes) throws IOException {
    super.serialize(stream, configIndexes, sourceFileIndexes, namespaceResolverIndexes);
    AarAttrResourceItem.serializeAttrValue(this, getRepository().getNamespace(), stream);
  }
}
