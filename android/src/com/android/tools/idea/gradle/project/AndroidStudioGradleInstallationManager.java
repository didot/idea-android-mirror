package com.android.tools.idea.gradle.project;

import com.android.tools.idea.sdk.IdeSdks;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.GradleInstallationManager;

public class AndroidStudioGradleInstallationManager extends GradleInstallationManager {

  @Nullable
  @Override
  public String getGradleJvmPath(@NotNull Project project, @NotNull String linkedProjectPath) {
    @Nullable Sdk jdk = IdeSdks.getInstance().getJdk();
    if (jdk == null) {
      return null;
    }
    return jdk.getHomePath();
  }
}
