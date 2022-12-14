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
package com.android.tools.idea.gradle.project.upgrade;

import static org.junit.Assert.assertEquals;

import com.android.ide.common.repository.GradleVersion;
import com.intellij.openapi.project.Project;
import java.util.Arrays;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Tests for {@link GradlePluginUpgrade#shouldRecommendPluginUpgrade(Project)}.
 */
@RunWith(Parameterized.class)
public class RecommendedPluginVersionUpgradeTest {
  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
      // Test for old AGP versions, which should all force rather than recommend.
      {"2.0.0-alpha9", "2.0.0-alpha9", false},
      {"1.1", "2.0.0", false},
      {"2.0.0-alpha9", "2.0.0-beta1", false},
      {"2.0.0-alpha9", "2.0.0", false},
      {"1.5.0-beta1", "2.0.0-alpha10", false},
      {"2.3.0-alpha1", "2.3.0-dev", false},
      {"1.5.0-beta1", "3.4.0", false},
      {"2.3.0-alpha1", "3.4.0", false},
      // We never suggest to upgrade from alpha/beta version to another alpha/beta version.
      // It is handled by force upgrade.
      {"3.3.0-alpha02", "3.3.0-alpha01", false},
      {"3.3.0-alpha01", "3.3.0-alpha02", false},
      {"3.3.0-beta01", "3.3.0-alpha01", false},
      {"3.3.0-alpha01", "3.3.0-beta01", false},
      {"3.3.0-beta01", "3.3.0-beta02", false},
      {"3.4.0-alpha01", "3.3.0-alpha01", false},
      {"3.4.0-alpha01", "3.3.0-beta01", false},
      {"3.4.0-beta01", "3.3.0-alpha01", false},
      {"3.4.0-beta01", "3.3.0-beta01", false},
      // Don't recommend to upgrade from stable version to newer alpha/beta version.
      {"3.3.0", "3.3.0-alpha1", false},
      {"3.3.0", "3.3.0-beta01", false},
      {"3.3.0", "3.4.0-alpha01", false},
      {"3.3.0", "3.4.0-beta01", false},
      // Never ask for upgrading from dev version to alpha/beta version. Dev version is for internal development only.
      {"3.3.0-dev", "3.3.0-alpha1", false},
      {"3.3.0-dev", "3.3.0-beta01", false},
      {"3.3.0-dev", "3.4.0-alpha01", false},
      {"3.3.0-dev", "3.4.0-beta01", false},
      // Upgrade to dev version. We only ask for upgrading to dev version when major versions are different.
      // (Note: Force upgrade doesn't upgrade to dev version either.)
      {"3.3.0", "3.3.0-dev", false},
      {"3.3.0-alpha01", "3.3.0-dev", false},
      {"3.3.0-beta01", "3.3.0-dev", false},
      {"3.3.0", "3.4.0-dev", false},
      {"3.3.0-alpha01", "3.4.0-dev", true},
      {"3.3.0-beta01", "3.4.0-dev", true},
      // upgrade to stable version.  Upgrades from alpha/beta are forced; upgrades from rc are recommended
      {"3.4.0-alpha01", "3.3.0", false},
      {"3.4.0-beta01", "3.3.0", false},
      {"3.4.0-rc01", "3.3.0", false},
      {"3.4.0-dev", "3.3.0", false},
      {"3.4.0", "3.3.0", false},
      {"3.4.0-alpha01", "3.4.0", false},
      {"3.4.0-beta01", "3.4.0", false},
      {"3.4.0-rc01", "3.4.0", true},
      {"3.4.0-dev", "3.4.0", false},
      {"3.3.0-rc01", "3.4.0", true},
      {"3.3.0", "3.4.0", true},
      {"3.3.0-rc01", "3.3.1", true},
      {"3.3.0", "3.3.1", true},
      {"3.3.0", "3.4.0-rc01", true},
      {"3.3.1", "3.3.0-rc01", false},
      {"3.3.1", "3.3.0", false},
      // Upgrades from unsupported stable version to new stable versions are forced.
      {"1.5.0", "3.4.0", false},
      {"2.2.0", "3.4.0", false},
      // Upgrades from long-ago stable version to new stable versions are recommended.
      {"3.3.0", "7.0.0", true},
    });
  }

  @NotNull private final GradleVersion myCurrent;
  @NotNull private final GradleVersion myRecommended;

  private final boolean myRecommendUpgrade;

  public RecommendedPluginVersionUpgradeTest(@NotNull String current, @NotNull String recommended, boolean recommendUpgrade) {
    myCurrent = GradleVersion.parse(current);
    myRecommended = GradleVersion.parse(recommended);
    myRecommendUpgrade = recommendUpgrade;
  }

  @Test
  public void shouldRecommendUpgrade() {
    boolean recommended = GradlePluginUpgrade.shouldRecommendUpgrade(myCurrent, myRecommended);
    assertEquals("should recommend upgrade from " + myCurrent + " to " + myRecommended + "?", myRecommendUpgrade, recommended);
  }
}