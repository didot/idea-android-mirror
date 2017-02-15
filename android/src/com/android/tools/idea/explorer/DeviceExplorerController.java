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
package com.android.tools.idea.explorer;

import com.android.tools.idea.explorer.adbimpl.AdbPathUtil;
import com.android.tools.idea.explorer.fs.*;
import com.android.utils.FileUtils;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.intellij.ui.UIBundle;
import com.intellij.util.Alarm;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import java.awt.datatransfer.StringSelection;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;


/**
 * Implementation of the Device Explorer application logic
 */
public class DeviceExplorerController {
  private static final Logger LOGGER = Logger.getInstance(DeviceExplorerController.class);
  private static final Key<DeviceExplorerController> KEY = Key.create(DeviceExplorerController.class.getName());
  private static final long FILE_ENTRY_CREATION_TIMEOUT_MILLIS = 10_000;

  private int myShowLoadingNodeDelayMillis = 200;
  private int myDownloadingNodeRepaintMillis = 100;

  @NotNull private final Project myProject;
  @NotNull private final DeviceExplorerModel myModel;
  @NotNull private final DeviceExplorerView myView;
  @NotNull private final DeviceFileSystemService myService;
  @NotNull private final FutureCallbackExecutor myEdtExecutor;
  @NotNull private final DeviceExplorerFileManager myFileManager;
  @NotNull private final Set<DeviceFileEntryNode> myDownloadingNodes = new HashSet<>();
  @NotNull private final Set<DeviceFileEntryNode> myLoadingChildren = new HashSet<>();
  @NotNull private final Alarm myLoadingNodesAlarms;
  @NotNull private final Alarm myDownloadingNodesAlarms;
  @NotNull private final Alarm myLoadingChildrenAlarms;

  public DeviceExplorerController(@NotNull Project project,
                                  @NotNull DeviceExplorerModel model,
                                  @NotNull DeviceExplorerView view,
                                  @NotNull DeviceFileSystemService service,
                                  @NotNull DeviceExplorerFileManager fileManager,
                                  @NotNull Executor edtExecutor) {
    myProject = project;
    myModel = model;
    myView = view;
    myService = service;
    myEdtExecutor = new FutureCallbackExecutor(edtExecutor);
    myService.addListener(new ServiceListener());
    myView.addListener(new ViewListener());
    myFileManager = fileManager;
    myLoadingNodesAlarms = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
    myDownloadingNodesAlarms = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
    myLoadingChildrenAlarms = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

    project.putUserData(KEY, this);
  }

  @Nullable
  public static DeviceExplorerController getProjectController(@Nullable Project project) {
    if (project == null) {
      return null;
    }
    return project.getUserData(KEY);
  }

  @Nullable
  private DefaultTreeModel getTreeModel() {
    return myModel.getTreeModel();
  }

  public void setup() {
    myView.setup();
    ListenableFuture<Void> future = myService.start();
    myEdtExecutor.addCallback(future, new FutureCallback<Void>() {
      @Override
      public void onSuccess(@Nullable Void result) {
        myView.serviceSetupSuccess();
        setupInitialView();
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        myView.reportErrorRelatedToService(myService, "Unable to start file system service", t);
      }
    });
  }

  public void restartService() {
    ListenableFuture<Void> futureResult = myService.restart();
    myEdtExecutor.addCallback(futureResult, new FutureCallback<Void>() {
      @Override
      public void onSuccess(@Nullable Void result) {
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        myView.reportErrorRelatedToService(myService, "Unable to restart file system service", t);
      }
    });
  }

  private void setupInitialView() {
    ListenableFuture<List<DeviceFileSystem>> future = myService.getDevices();
    myEdtExecutor.addCallback(future, new FutureCallback<List<DeviceFileSystem>>() {
      @Override
      public void onSuccess(List<DeviceFileSystem> result) {
        result.forEach(myModel::addDevice);
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        myView.reportErrorRelatedToService(myService, "Unable to get list of devices", t);
      }
    });
  }

  private void setActiveDevice(@Nullable DeviceFileSystem device) {
    myLoadingNodesAlarms.cancelAllRequests();
    myLoadingChildrenAlarms.cancelAllRequests();
    myDownloadingNodesAlarms.cancelAllRequests();
    myLoadingChildren.clear();
    myDownloadingNodes.clear();

    myModel.setActiveDevice(device);
    ListenableFuture<DefaultTreeModel> futureTreeModel = createTreeModel(device);
    myEdtExecutor.addCallback(futureTreeModel, new FutureCallback<DefaultTreeModel>() {
      @Override
      public void onSuccess(@Nullable DefaultTreeModel result) {
        myModel.setActiveDeviceTreeModel(device, result);
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        assert device != null; // Never fails for "null" device
        myModel.setActiveDeviceTreeModel(device, null);
        myView.reportErrorRelatedToDevice(device, "Unable to get root directory of device", t);
      }
    });
  }

  @NotNull
  private ListenableFuture<DefaultTreeModel> createTreeModel(@Nullable DeviceFileSystem device) {
    SettableFuture<DefaultTreeModel> futureResult = SettableFuture.create();
    if (device == null) {
      futureResult.set(null);
      return futureResult;
    }

    ListenableFuture<DeviceFileEntry> futureRoot = device.getRootDirectory();
    myEdtExecutor.addCallback(futureRoot, new FutureCallback<DeviceFileEntry>() {
      @Override
      public void onSuccess(DeviceFileEntry result) {
        DeviceFileEntryNode rootNode = new DeviceFileEntryNode(result);
        DefaultTreeModel model = new DefaultTreeModel(rootNode);
        futureResult.set(model);
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        futureResult.setException(t);
      }
    });
    return futureResult;
  }

  private void startDownloadNode(@NotNull DeviceFileEntryNode node) {
    myView.startTreeBusyIndicator();
    node.setDownloading(true);
    if (myDownloadingNodes.size() == 0) {
      myDownloadingNodesAlarms.addRequest(new MyDownloadingNodesRepaint(), myDownloadingNodeRepaintMillis);
    }
    myDownloadingNodes.add(node);
  }

  private void stopDownloadNode(@NotNull DeviceFileEntryNode node) {
    myView.stopTreeBusyIndicator();
    node.setDownloading(false);
    if (getTreeModel() != null) {
      getTreeModel().nodeChanged(node);
    }
    myDownloadingNodes.remove(node);
    if (myDownloadingNodes.size() == 0) {
      myDownloadingNodesAlarms.cancelAllRequests();
    }
  }

  private void startLoadChildren(@NotNull DeviceFileEntryNode node) {
    myView.startTreeBusyIndicator();
    if (myLoadingChildren.size() == 0) {
      myLoadingChildrenAlarms.addRequest(new MyLoadingChildrenRepaint(), myDownloadingNodeRepaintMillis);
    }
    myLoadingChildren.add(node);
  }

  private void stopLoadChildren(@NotNull DeviceFileEntryNode node) {
    myView.stopTreeBusyIndicator();
    myLoadingChildren.remove(node);
    if (myLoadingChildren.size() == 0) {
      myLoadingChildrenAlarms.cancelAllRequests();
    }
  }

  public boolean hasActiveDevice() {
    return myModel.getActiveDevice() != null;
  }

  @TestOnly
  @SuppressWarnings("SameParameterValue")
  public void setShowLoadingNodeDelayMillis(int showLoadingNodeDelayMillis) {
    myShowLoadingNodeDelayMillis = showLoadingNodeDelayMillis;
  }

  @TestOnly
  @SuppressWarnings("SameParameterValue")
  public void setDownloadingNodeRepaintMillis(int downloadingNodeRepaintMillis) {
    myDownloadingNodeRepaintMillis = downloadingNodeRepaintMillis;
  }

  private class ServiceListener implements DeviceFileSystemServiceListener {
    @Override
    public void updated() {
      myLoadingNodesAlarms.cancelAllRequests();
      myModel.removeAllDevices();
      setupInitialView();
    }

    @Override
    public void deviceAdded(@NotNull DeviceFileSystem device) {
      myModel.addDevice(device);
    }

    @Override
    public void deviceRemoved(@NotNull DeviceFileSystem device) {
      myModel.removeDevice(device);
    }

    @Override
    public void deviceUpdated(@NotNull DeviceFileSystem device) {
      myModel.updateDevice(device);
    }
  }

  private class ViewListener implements DeviceExplorerViewListener {
    @Override
    public void deviceSelected(@Nullable DeviceFileSystem device) {
      setActiveDevice(device);
    }

    @Override
    public void openNodeInEditorInvoked(@NotNull DeviceFileEntryNode treeNode) {
      if (treeNode.getEntry().isDirectory()) {
        return;
      }

      if (treeNode.isDownloading()) {
        myView.reportErrorRelatedToNode(treeNode, "Entry is already downloading", new RuntimeException());
        return;
      }

      ListenableFuture<Path> futurePath = downloadFileEntry(treeNode, false);
      myEdtExecutor.addCallback(futurePath, new FutureCallback<Path>() {
        @Override
        public void onSuccess(@Nullable Path localPath) {
          assert localPath != null;
          try {
            myFileManager.openFileInEditor(localPath, true);
          } catch(Throwable t) {
            String message = String.format("Unable to open file \"%s\" in editor", localPath);
            myView.reportErrorRelatedToNode(treeNode, message, t);
          }
        }

        @Override
        public void onFailure(@NotNull Throwable t) {
          String message = String.format("Error downloading contents of device file %s", getUserFacingNodeName(treeNode));
          myView.reportErrorRelatedToNode(treeNode, message, t);
        }
      });
    }

    @Override
    public void saveNodeAsInvoked(@NotNull DeviceFileEntryNode treeNode) {
      if (treeNode.getEntry().isDirectory()) {
        return;
      }

      if (treeNode.isDownloading()) {
        myView.reportErrorRelatedToNode(treeNode, "Entry is already downloading", new RuntimeException());
        return;
      }

      ListenableFuture<Path> futurePath = downloadFileEntry(treeNode, true);
      myEdtExecutor.addCallback(futurePath, new FutureCallback<Path>() {
        @Override
        public void onSuccess(@Nullable Path localPath) {
          assert localPath != null;
          String message = String.format("Device file %s successfully downloaded to local file \"%s\"",
                                         getUserFacingNodeName(treeNode),
                                         localPath);
          myView.reportMessageRelatedToNode(treeNode, message);
        }

        @Override
        public void onFailure(@NotNull Throwable t) {
          String message = String.format("Error saving contents of device file %s", getUserFacingNodeName(treeNode));
          myView.reportErrorRelatedToNode(treeNode, message, t);
        }
      });
    }

    @Override
    public void copyNodePathInvoked(@NotNull DeviceFileEntryNode treeNode) {
      CopyPasteManager.getInstance().setContents(new StringSelection(treeNode.getEntry().getFullPath()));
    }


    @Override
    public void newFileInvoked(@NotNull DeviceFileEntryNode parentTreeNode) {
      newFileOrDirectory(parentTreeNode,
                         "NewTextFile.txt",
                         UIBundle.message("new.file.dialog.title"),
                         UIBundle.message("create.new.file.enter.new.file.name.prompt.text"),
                         UIBundle.message("create.new.file.file.name.cannot.be.empty.error.message"),
                         x -> UIBundle.message("create.new.file.could.not.create.file.error.message", x),
                         x -> parentTreeNode.getEntry().getFileSystem().createNewFile(parentTreeNode.getEntry(), x));
    }

    @Override
    public void newDirectoryInvoked(@NotNull DeviceFileEntryNode parentTreeNode) {
      newFileOrDirectory(parentTreeNode,
                         "NewFolder",
                         UIBundle.message("new.folder.dialog.title"),
                         UIBundle.message("create.new.folder.enter.new.folder.name.prompt.text"),
                         UIBundle.message("create.new.folder.folder.name.cannot.be.empty.error.message"),
                         x -> UIBundle.message("create.new.folder.could.not.create.folder.error.message", x),
                         x -> parentTreeNode.getEntry().getFileSystem().createNewDirectory(parentTreeNode.getEntry(), x));
    }

    private void newFileOrDirectory(@NotNull DeviceFileEntryNode parentTreeNode,
                                    @NotNull String initialName,
                                    @NotNull String title,
                                    @NotNull String prompt,
                                    @NotNull String emptyErrorMessage,
                                    @NotNull Function<String, String> errorMessage,
                                    @NotNull Function<String, ListenableFuture<Void>> createFunction) {
      DefaultTreeModel treeModel = getTreeModel();
      if (treeModel == null) {
        return;
      }

      while (true) {
        String newFileName = Messages.showInputDialog(prompt, title, Messages.getQuestionIcon(), initialName, new InputValidatorEx() {
          @Nullable
          @Override
          public String getErrorText(String inputString) {
            if (StringUtil.isEmpty(inputString.trim())) {
              return emptyErrorMessage;
            }
            else if (inputString.contains(AdbPathUtil.FILE_SEPARATOR)) {
              return "Path cannot contain \"/\" characters";
            }
            return null;
          }

          @Override
          public boolean checkInput(String inputString) {
            return canClose(inputString);
          }

          @Override
          public boolean canClose(String inputString) {
            return !StringUtil.isEmpty(inputString.trim());
          }
        });
        if (newFileName == null) {
          return;
        }

        ListenableFuture<Void> futureResult = createFunction.apply(newFileName);
        try {
          futureResult.get(FILE_ENTRY_CREATION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

          // Refresh the parent node to show the newly created file
          parentTreeNode.setLoaded(false);
          loadNodeChildren(parentTreeNode);
        }
        catch(ExecutionException | InterruptedException | TimeoutException e) {
          showErrorMessage(errorMessage.apply(newFileName), e);
          initialName = newFileName;
          continue;  // Try again
        }
        return;
      }
    }

    private void showErrorMessage(@NotNull String message, @NotNull Throwable error) {
      // Execution exceptions contain the actual cause of the error
      if (error instanceof ExecutionException) {
        if (error.getCause() != null) {
          error = error.getCause();
        }
      }
      // Add error message from execption if we have one
      if (error.getMessage() != null) {
        message += ":\n" + error.getMessage();
      }

      // Show error dialog
      Messages.showMessageDialog(message, UIBundle.message("error.dialog.title"), Messages.getErrorIcon());
    }

    @NotNull
    private ListenableFuture<Path> downloadFileEntry(@NotNull DeviceFileEntryNode treeNode, boolean askForLocation) {
      SettableFuture<Path> futureResult = SettableFuture.create();

      // Figure out local path, ask user in "Save As" dialog if required
      Path localPath;
      try {
        if (askForLocation) {
          localPath = chooseSaveAsLocalPath(treeNode.getEntry());
        }
        else {
          localPath = myFileManager.getDefaultLocalPathForEntry(treeNode.getEntry());
        }
      }
      catch (Throwable t) {
        futureResult.setException(t);
        return futureResult;
      }

      // Download the entry to the local path
      DeviceFileEntry entry = treeNode.getEntry();
      startDownloadNode(treeNode);
      ListenableFuture<Void> future = myFileManager.downloadFileEntry(entry, localPath, new FileTransferProgress() {
        @Override
        public void progress(long currentBytes, long totalBytes) {
          treeNode.setDownloadProgress(currentBytes, totalBytes);
        }

        @Override
        public boolean isCancelled() {
          return false;
        }
      });

      myEdtExecutor.addCallback(future, new FutureCallback<Void>() {
        @Override
        public void onSuccess(@Nullable Void result) {
          stopDownloadNode(treeNode);
          futureResult.set(localPath);
        }

        @Override
        public void onFailure(@NotNull Throwable t) {
          stopDownloadNode(treeNode);
          futureResult.setException(t);
        }
      });

      return futureResult;
    }

    @NotNull
    private Path chooseSaveAsLocalPath(@NotNull DeviceFileEntry entry) {
      Path localPath = myFileManager.getDefaultLocalPathForEntry(entry);

      FileUtils.mkdirs(localPath.getParent().toFile());
      VirtualFile baseDir = VfsUtil.findFileByIoFile(localPath.getParent().toFile(), true);
      if (baseDir == null) {
        throw new RuntimeException(String.format("Unable to locate file \"%s\"", localPath.getParent()));
      }

      FileSaverDescriptor descriptor = new FileSaverDescriptor("Save As", "");
      FileSaverDialog saveFileDialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, myProject);
      VirtualFileWrapper fileWrapper = saveFileDialog.save(baseDir, localPath.getFileName().toString());
      if (fileWrapper == null) {
        throw new CancellationException();
      }
      return fileWrapper.getFile().toPath();
    }

    @Override
    public void treeNodeExpanding(@NotNull DeviceFileEntryNode node) {
      loadNodeChildren(node);
    }

    private void loadNodeChildren(@NotNull final DeviceFileEntryNode node) {
      // Ensure node is expanded only once
      if (node.isLoaded()) {
        return;
      }
      node.setLoaded(true);

      // Leaf nodes are not expandable
      if (node.isLeaf()) {
        return;
      }

      DefaultTreeModel treeModel = getTreeModel();
      if (treeModel == null) {
        return;
      }

      DeviceFileSystem fileSystem = myModel.getActiveDevice();
      if (!Objects.equals(fileSystem, node.getEntry().getFileSystem())) {
        return;
      }

      ShowLoadingNodeRequest showLoadingNode = new ShowLoadingNodeRequest(treeModel, node);
      myLoadingNodesAlarms.addRequest(showLoadingNode, myShowLoadingNodeDelayMillis);

      startLoadChildren(node);
      ListenableFuture<List<DeviceFileEntry>> futureEntries = node.getEntry().getEntries();
      myEdtExecutor.addCallback(futureEntries, new FutureCallback<List<DeviceFileEntry>>() {
        @Override
        public void onSuccess(List<DeviceFileEntry> result) {
          stopLoadChildren(node);
          myLoadingNodesAlarms.cancelRequest(showLoadingNode);

          List<DeviceFileEntryNode> nodes = result.stream().map(DeviceFileEntryNode::new).collect(Collectors.toList());
          node.removeAllChildren();
          node.setAllowsChildren(nodes.size() > 0);
          nodes.forEach(node::add);
          treeModel.nodeStructureChanged(node);

          List<DeviceFileEntryNode> symlinkNodes = nodes
            .stream()
            .filter(x -> x.getEntry().isSymbolicLink())
            .collect(Collectors.toList());
          querySymbolicLinks(symlinkNodes, treeModel);
        }

        @Override
        public void onFailure(@NotNull Throwable t) {
          stopLoadChildren(node);
          myLoadingNodesAlarms.cancelRequest(showLoadingNode);
          String message = String.format("Unable to list entries of directory %s", getUserFacingNodeName(node));
          myView.reportErrorRelatedToNode(node, message, t);

          node.removeAllChildren();
          node.add(new ErrorNode(message));
          node.setAllowsChildren(true);
          treeModel.nodeStructureChanged(node);
        }
      });
    }

    /**
     * Asynchronously update the tree node UI of the {@code symlinkNodes} entries if they target
     * a directory, i.e. update tree nodes with a "Folder" and "Expandable arrow" icon.
     */
    private void querySymbolicLinks(@NotNull List<DeviceFileEntryNode> symlinkNodes, @NotNull DefaultTreeModel treeModel) {
      querySymbolicLinksWorker(symlinkNodes, 0, treeModel);
    }

    private void querySymbolicLinksWorker(@NotNull List<DeviceFileEntryNode> symlinkNodes, int nodeIndex, @NotNull DefaultTreeModel treeModel) {
      if (nodeIndex >= symlinkNodes.size()) {
        return;
      }

      // Note: We process (asynchronously) one entry at a time, instead of all of them in parallel,
      //       to avoid flooding the device with too many requests, which would eventually lead
      //       to the device to reject additional requests.
      DeviceFileEntryNode treeNode = symlinkNodes.get(nodeIndex);
      ListenableFuture<Boolean> future = treeNode.getEntry().isSymbolicLinkToDirectory();
      myEdtExecutor.addConsumer(future, (@Nullable Boolean result, @Nullable Throwable throwable) -> {
        // Log error, but keep going as we may have more symlinkNodes to examine
        if (throwable != null) {
          LOGGER.info(String.format("Error determining if file entry \"%s\" is a link to a directory",
                                    treeNode.getEntry().getName()),
                                    throwable);
        }

        // Stop all processing if tree model has changed, i.e. UI has been switched to another device
        if (!Objects.equals(myModel.getTreeModel(), treeModel)) {
          return;
        }

        // Update tree node appearance (in case of "null"" result, we assume the entry
        // does not target a directory).
        boolean isDirectory = result != null && result;
        treeNode.setSymbolicLinkToDirectory(isDirectory);
        treeModel.nodeStructureChanged(treeNode);

        // Asynchronously process the next symlink
        querySymbolicLinksWorker(symlinkNodes, nodeIndex + 1, treeModel);
      });
    }

    @NotNull
    private String getUserFacingNodeName(@NotNull DeviceFileEntryNode node) {
      return StringUtil.isEmpty(node.getEntry().getName()) ?
             "[root]" :
             "\"" + node.getEntry().getName() + "\"";
    }
  }

  private static class ShowLoadingNodeRequest implements Runnable {
    @NotNull private DefaultTreeModel myTreeModel;
    @NotNull private DeviceFileEntryNode myNode;

    public ShowLoadingNodeRequest(@NotNull DefaultTreeModel treeModel, @NotNull DeviceFileEntryNode node) {
      myTreeModel = treeModel;
      myNode = node;
    }

    @Override
    public void run() {
      myNode.add(new MyLoadingNode(myNode.getEntry()));
      myTreeModel.nodeStructureChanged(myNode);
    }
  }

  private class MyDownloadingNodesRepaint implements Runnable {
    @Override
    public void run() {
      myDownloadingNodes.forEach(x ->  {
        x.incDownloadingTick();
        if (getTreeModel() != null) {
          getTreeModel().nodeChanged(x);
        }
      });
      myDownloadingNodesAlarms.addRequest(new MyDownloadingNodesRepaint(), myDownloadingNodeRepaintMillis);
    }
  }

  private class MyLoadingChildrenRepaint implements Runnable {
    @Override
    public void run() {
      myLoadingChildren.forEach(x ->  {
        if (x.getChildCount() == 0)
          return;

        TreeNode node = x.getFirstChild();
        if (node instanceof MyLoadingNode) {
          MyLoadingNode ladingNode = (MyLoadingNode)node;
          ladingNode.incDownloadingTick();
          if (getTreeModel() != null) {
            getTreeModel().nodeChanged(ladingNode);
          }
        }
      });
      myLoadingChildrenAlarms.addRequest(new MyLoadingChildrenRepaint(), myDownloadingNodeRepaintMillis);
    }
  }
}
