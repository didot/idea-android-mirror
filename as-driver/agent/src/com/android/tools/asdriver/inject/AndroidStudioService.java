/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.asdriver.inject;

import com.android.tools.asdriver.proto.ASDriver;
import com.android.tools.asdriver.proto.AndroidStudioGrpc;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.CountDownLatch;

public class AndroidStudioService extends AndroidStudioGrpc.AndroidStudioImplBase {

  static public void start() {
    //TODO: Use a dynamic port and report it to the client via stdout.
    ServerBuilder<?> builder = ServerBuilder.forPort(5678);
    builder.addService(new AndroidStudioService());
    Server server = builder.build();

    new Thread(() -> {
      try {
        server.start();
      }
      catch (Throwable t) {
        t.printStackTrace();
      }
    }).start();
  }

  @Override
  public void getVersion(ASDriver.GetVersionRequest request, StreamObserver<ASDriver.GetVersionResponse> responseObserver) {
    ApplicationInfo info = ApplicationInfo.getInstance();
    responseObserver.onNext(ASDriver.GetVersionResponse.newBuilder().setVersion(info.getFullApplicationName()).build());
    responseObserver.onCompleted();
  }

  @Override
  public void kill(ASDriver.KillRequest request, StreamObserver<ASDriver.KillResponse> responseObserver) {
    System.exit(request.getExitCode());
  }

  @Override
  public void executeAction(ASDriver.ExecuteActionRequest request, StreamObserver<ASDriver.ExecuteActionResponse> responseObserver) {
    ASDriver.ExecuteActionResponse.Builder builder = ASDriver.ExecuteActionResponse.newBuilder();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      AnAction action = ActionManager.getInstance().getAction(request.getActionId());
      if (action != null) {
        DataContext dataContext = DataManager.getInstance().getDataContext();
        AnActionEvent event = AnActionEvent.createFromAnAction(action, null, ActionPlaces.UNKNOWN, dataContext);
        ActionUtil.performActionDumbAwareWithCallbacks(action, event, dataContext);
        builder.setResult(ASDriver.ExecuteActionResponse.Result.OK);
      } else {
        builder.setResult(ASDriver.ExecuteActionResponse.Result.ACTION_NOT_FOUND);
      }
    });
    responseObserver.onNext(builder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void showToolWindow(ASDriver.ShowToolWindowRequest request, StreamObserver<ASDriver.ShowToolWindowResponse> responseObserver) {
    ASDriver.ShowToolWindowResponse.Builder builder = ASDriver.ShowToolWindowResponse.newBuilder();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      boolean found = false;
      for (Project p : ProjectManager.getInstance().getOpenProjects()) {
        found = true;
        ToolWindow window = ToolWindowManagerEx.getInstanceEx(p).getToolWindow(request.getToolWindowId());
        if (window != null) {
          window.show();
          builder.setResult(ASDriver.ShowToolWindowResponse.Result.OK);
        } else {
          builder.setResult(ASDriver.ShowToolWindowResponse.Result.TOOL_WINDOW_NOT_FOUND);
        }
        break;
      }
      if (!found) {
        builder.setResult(ASDriver.ShowToolWindowResponse.Result.PROJECT_NOT_FOUND);
      }
    });
    responseObserver.onNext(builder.build());
    responseObserver.onCompleted();
  }
}
