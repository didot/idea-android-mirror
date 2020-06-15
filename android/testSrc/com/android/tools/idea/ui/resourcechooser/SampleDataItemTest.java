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
package com.android.tools.idea.ui.resourcechooser;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.SingleNamespaceResourceRepository;
import com.android.resources.ResourceType;
import com.android.tools.idea.res.PredefinedSampleDataResourceRepository;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.android.tools.idea.res.SampleDataResourceItem;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.google.common.truth.Truth;
import com.intellij.mock.MockPsiFile;
import com.intellij.mock.MockPsiManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.Computable;
import com.intellij.testFramework.LightVirtualFile;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

public class SampleDataItemTest {
  @Rule
  public AndroidProjectRule rule = AndroidProjectRule.inMemory();

  @Test
  public void getResourceUrl() {
    SingleNamespaceResourceRepository repository = Mockito.mock(SingleNamespaceResourceRepository.class);
    Mockito.when(repository.getNamespace()).thenReturn(ResourceNamespace.RES_AUTO);

    MockPsiFile file = new MockPsiFile(new LightVirtualFile("dummy"), new MockPsiManager(rule.getProject()));
    file.putUserData(ModuleUtilCore.KEY_MODULE, rule.getModule());
    file.text = "toto\ntata";
    ResourceChooserItem.SampleDataItem userItem = new ResourceChooserItem.SampleDataItem(ApplicationManager.getApplication().runReadAction(
      (Computable<SampleDataResourceItem>)() -> {
        try {
          return SampleDataResourceItem.getFromPsiFileSystemItem(repository, file).get(0);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }));
    Truth.assertThat(userItem.getResourceUrl()).isEqualTo("@sample/dummy");

    ResourceItem predefinedItem = ResourceRepositoryManager
      .getAppResources(rule.getModule())
      .getResources(PredefinedSampleDataResourceRepository.NAMESPACE, ResourceType.SAMPLE_DATA, "lorem")
      .get(0);
    ResourceChooserItem.SampleDataItem dataItem = new ResourceChooserItem.SampleDataItem((SampleDataResourceItem)predefinedItem);
    Truth.assertThat(dataItem.getResourceUrl()).isEqualTo("@tools:sample/lorem");
  }
}
