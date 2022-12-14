/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.sdk;

import com.android.tools.idea.sdk.AndroidSdks;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.projectRoots.AdditionalDataConfigurable;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModel;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.util.io.FileUtil;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;

public class AndroidSdkConfigurable implements AdditionalDataConfigurable {
  private final AndroidSdkConfigurableForm myForm;

  private Sdk mySdk;
  private final SdkModel.Listener myListener;
  private final SdkModel mySdkModel;

  public AndroidSdkConfigurable(@NotNull SdkModel sdkModel, @NotNull SdkModificator sdkModificator) {
    mySdkModel = sdkModel;
    myForm = new AndroidSdkConfigurableForm(sdkModel, sdkModificator);
    myListener = new SdkModel.Listener() {
      @Override
      public void sdkAdded(@NotNull Sdk sdk) {
        if (sdk.getSdkType().equals(JavaSdk.getInstance())) {
          myForm.addJavaSdk(sdk);
        }
      }

      @Override
      public void beforeSdkRemove(@NotNull Sdk sdk) {
        if (sdk.getSdkType().equals(JavaSdk.getInstance())) {
          myForm.removeJavaSdk(sdk);
        }
      }

      @Override
      public void sdkChanged(@NotNull Sdk sdk, String previousName) {
        if (sdk.getSdkType().equals(JavaSdk.getInstance())) {
          myForm.updateJdks(sdk, previousName);
        }
      }

      @Override
      public void sdkHomeSelected(@NotNull Sdk sdk, @NotNull String newSdkHome) {
        if (sdk != null && AndroidSdks.getInstance().isAndroidSdk(sdk)) {
          myForm.internalJdkUpdate(sdk);
        }
      }
    };
    mySdkModel.addListener(myListener);
  }

  @Override
  public void setSdk(Sdk sdk) {
    mySdk = sdk;
  }

  @Override
  public JComponent createComponent() {
    return myForm.getContentPanel();
  }

  @Override
  public boolean isModified() {
    AndroidSdkAdditionalData data = AndroidSdks.getInstance().getAndroidSdkAdditionalData(mySdk);
    Sdk javaSdk = data != null ? data.getJavaSdk() : null;
    String javaSdkHomePath = javaSdk != null ? javaSdk.getHomePath() : null;
    Sdk selectedSdk = myForm.getSelectedSdk();
    String selectedSdkHomePath = selectedSdk != null ? selectedSdk.getHomePath() : null;
    return !FileUtil.pathsEqual(javaSdkHomePath, selectedSdkHomePath);
  }

  @Override
  public void apply() throws ConfigurationException {
    Sdk javaSdk = myForm.getSelectedSdk();
    AndroidSdkAdditionalData newData = new AndroidSdkAdditionalData(mySdk, javaSdk);
    newData.setBuildTarget(myForm.getSelectedBuildTarget());
    SdkModificator modificator = mySdk.getSdkModificator();
    modificator.setVersionString(javaSdk != null ? javaSdk.getVersionString() : null);
    modificator.setSdkAdditionalData(newData);
    ApplicationManager.getApplication().runWriteAction(modificator::commitChanges);
  }

  @Override
  public void reset() {
    if (mySdk == null) {
      return;
    }
    AndroidSdkAdditionalData data = AndroidSdks.getInstance().getAndroidSdkAdditionalData(mySdk);
    if (data == null) {
      return;
    }
    AndroidPlatform platform = data.getAndroidPlatform();
    myForm.init(data.getJavaSdk(), mySdk, platform != null ? data.getBuildTarget(platform.getSdkData()) : null);
  }

  @Override
  public void disposeUIResources() {
    mySdkModel.removeListener(myListener);
  }
}
