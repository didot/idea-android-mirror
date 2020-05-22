package org.jetbrains.android.dom.layout;

import com.intellij.openapi.module.Module;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ViewLayoutDomFileDescription extends LayoutDomFileDescription<LayoutViewElement> {
  public ViewLayoutDomFileDescription() {
    super(LayoutViewElement.class, "view");
  }

  @Override
  public boolean checkFile(@NotNull XmlFile file, @Nullable Module module) {
    return !FragmentLayoutDomFileDescription.hasFragmentRootTag(file) &&
           !DataBindingDomFileDescription.hasDataBindingRootTag(file) &&
           !MergeDomFileDescription.Companion.hasMergeRootTag(file) &&
           !ViewTagDomFileDescription.Companion.hasViewRootTag(file);
  }
}

