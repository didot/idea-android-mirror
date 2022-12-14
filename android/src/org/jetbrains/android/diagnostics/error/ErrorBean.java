// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.diagnostics.error;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.util.ExceptionUtil;
import java.util.List;

public final class ErrorBean {
  private final String stackTrace;
  private final String lastAction;
  private String message;
  private String description;
  private String pluginName;
  private String pluginVersion;
  private List<Attachment> attachments = List.of();

  public ErrorBean(Throwable throwable, String lastAction) {
    this.stackTrace = throwable != null ? ExceptionUtil.getThrowableText(throwable) : null;
    this.lastAction = lastAction;
    if (throwable != null) {
      setMessage(throwable.getMessage());
    }
  }

  public String getStackTrace() {
    return stackTrace;
  }

  public String getLastAction() {
    return lastAction;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getPluginName() {
    return pluginName;
  }

  public void setPluginName(String pluginName) {
    this.pluginName = pluginName;
  }

  public String getPluginVersion() {
    return pluginVersion;
  }

  public void setPluginVersion(String pluginVersion) {
    this.pluginVersion = pluginVersion;
  }

  public List<Attachment> getAttachments() {
    return attachments;
  }

  public void setAttachments(List<Attachment> attachments) {
    this.attachments = attachments;
  }
}
