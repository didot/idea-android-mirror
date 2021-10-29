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
package com.android.tools.idea.avdmanager;

import static com.android.sdklib.internal.avd.AvdManager.AVD_INI_RESIZABLE_CONFIG;
import static com.google.common.truth.Truth.assertThat;

import com.android.repository.Revision;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.impl.meta.TypeDetails;
import com.android.repository.testframework.FakePackage;
import com.android.repository.testframework.FakePackage.FakeLocalPackage;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.FakeRepoManager;
import com.android.repository.testframework.MockFileOp;
import com.android.resources.ScreenOrientation;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.DeviceManager;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.sdklib.repository.targets.SystemImage;
import com.android.sdklib.repository.targets.SystemImageManager;
import com.android.testutils.MockLog;
import com.android.testutils.NoErrorsOrWarningsLogger;
import com.android.testutils.file.InMemoryFileSystems;
import com.android.utils.NullLogger;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.openapi.util.SystemInfo;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.jetbrains.android.AndroidTestCase;

public class AvdManagerConnectionTest extends AndroidTestCase {
  private static final String ANDROID_PREFS_ROOT = "android-home";

  private AvdManager mAvdManager;
  private AvdManagerConnection mAvdManagerConnection;
  private Path mAvdFolder;
  private SystemImage mSystemImage;
  private final MockFileOp mFileOp = new MockFileOp();

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    mFileOp.recordExistingFile("/sdk/tools/lib/emulator/snapshots.img");
    recordGoogleApisSysImg23(mFileOp);
    recordEmulatorVersion_23_4_5(mFileOp);
    Path root = InMemoryFileSystems.getSomeRoot(mFileOp.getFileSystem());

    AndroidSdkHandler androidSdkHandler = new AndroidSdkHandler(root.resolve("sdk"), mFileOp.toPath(ANDROID_PREFS_ROOT), mFileOp);

    mAvdManager = AvdManager.getInstance(androidSdkHandler, root.resolve(ANDROID_PREFS_ROOT + "/avd"), new NullLogger());

    assert mAvdManager != null;
    mAvdFolder = AvdInfo.getDefaultAvdFolder(mAvdManager, getName(), false);

    mSystemImage = androidSdkHandler.getSystemImageManager(new FakeProgressIndicator()).getImages().iterator().next();

    mAvdManagerConnection = new AvdManagerConnection(androidSdkHandler, mAvdFolder, MoreExecutors.newDirectExecutorService());
  }

  public void testResizableAvd() {
    String AVD_LOCATION = "/avd";
    String SDK_LOCATION = "/sdk";
    RepositoryPackages packages = new RepositoryPackages();

    // google api31 image
    String g31Path = "system-images;android-31;google_apis;x86_64";
    FakeLocalPackage g31Package = new FakeLocalPackage(g31Path, mFileOp.toPath("/sdk/mySysImg"));
    DetailsTypes.SysImgDetailsType g31Details = AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    g31Details.getTags().add(IdDisplay.create("google_apis", "Google APIs"));
    g31Package.setTypeDetails((TypeDetails)g31Details);
    mFileOp.recordExistingFile(g31Package.getLocation().resolve(SystemImageManager.SYS_IMG_NAME));

    packages.setLocalPkgInfos(ImmutableList.of(g31Package));
    FakeRepoManager mgr = new FakeRepoManager(mFileOp.toPath(SDK_LOCATION), packages);
    AndroidSdkHandler sdkHandler =
      new AndroidSdkHandler(mFileOp.toPath(SDK_LOCATION), mFileOp.toPath(AVD_LOCATION), mFileOp, mgr);
    FakeProgressIndicator progress = new FakeProgressIndicator();
    SystemImageManager systemImageManager = sdkHandler.getSystemImageManager(progress);

    ISystemImage g31Image =
      systemImageManager.getImageAt(Objects.requireNonNull(sdkHandler.getLocalPackage(g31Path, progress)).getLocation());
    assert g31Image != null;
    SystemImageDescription g31ImageDescription = new SystemImageDescription(g31Image);

    DeviceManager devMgr = DeviceManager.createInstance(sdkHandler, new NoErrorsOrWarningsLogger());
    Device resizableDevice = devMgr.getDevice("resizable", "Generic");

    Map<String, String> hardwareProperties = DeviceManager.getHardwareProperties(resizableDevice);

    mAvdManagerConnection.createOrUpdateAvd(
      null,
      "testResizable",
      resizableDevice,
      g31ImageDescription,
      ScreenOrientation.PORTRAIT,
      false,
      null,
      null,
      hardwareProperties,
      false);
    assertThat(hardwareProperties.get(AVD_INI_RESIZABLE_CONFIG)).
      isEqualTo("phone-0-1080-2340-420, foldable-1-1768-2208-420, tablet-2-1920-1200-240, desktop-3-1920-1080-160");
  }

  public void testWipeAvd() {
    MockLog log = new MockLog();
    // Create an AVD
    AvdInfo avd = mAvdManager.createAvd(
      mAvdFolder,
      getName(),
      mSystemImage,
      null,
      null,
      null,
      null,
      null,
      false,
      false,
      false,
      log);

    assertNotNull("Could not create AVD", avd);

    // Make a userdata-qemu.img so we can see if 'wipe-data' deletes it
    Path userQemu = mAvdFolder.resolve(AvdManager.USERDATA_QEMU_IMG);
    InMemoryFileSystems.recordExistingFile(userQemu);
    assertTrue("Could not create " + AvdManager.USERDATA_QEMU_IMG + " in " + mAvdFolder, Files.exists(userQemu));
    // Also make a 'snapshots' sub-directory with a file
    Path snapshotsDir = mAvdFolder.resolve(AvdManager.SNAPSHOTS_DIRECTORY);
    Path snapshotFile = snapshotsDir.resolve("aSnapShotFile.txt");
    InMemoryFileSystems.recordExistingFile(snapshotFile, "Some contents for the file");
    assertTrue("Could not create " + snapshotFile, Files.exists(snapshotFile));

    // Do the "wipe-data"
    assertTrue("Could not wipe data from AVD", mAvdManagerConnection.wipeUserData(avd));

    assertFalse("Expected NO " + AvdManager.USERDATA_QEMU_IMG + " in " + mAvdFolder + " after wipe-data", Files.exists(userQemu));
    assertFalse("wipe-data did not remove the '" + AvdManager.SNAPSHOTS_DIRECTORY + "' directory", Files.exists(snapshotsDir));

    Path userData = mAvdFolder.resolve(AvdManager.USERDATA_IMG);
    assertTrue("Expected " + AvdManager.USERDATA_IMG + " in " + mAvdFolder + " after wipe-data", Files.exists(userData));
  }

  public void testEmulatorVersionIsAtLeast() {
    // The emulator was created with version 23.4.5
    assertTrue(mAvdManagerConnection.emulatorVersionIsAtLeast(new Revision(22, 9, 9)));
    assertTrue(mAvdManagerConnection.emulatorVersionIsAtLeast(new Revision(23, 1, 9)));
    assertTrue(mAvdManagerConnection.emulatorVersionIsAtLeast(new Revision(23, 4, 5)));

    assertFalse(mAvdManagerConnection.emulatorVersionIsAtLeast(new Revision(23, 4, 6)));
    assertFalse(mAvdManagerConnection.emulatorVersionIsAtLeast(new Revision(23, 5, 1)));
    assertFalse(mAvdManagerConnection.emulatorVersionIsAtLeast(new Revision(24, 1, 1)));
  }

  public void testGetHardwareProperties() {
    recordEmulatorHardwareProperties(mFileOp);
    assertEquals("800M", mAvdManagerConnection.getSdCardSizeFromHardwareProperties());
    assertEquals("2G", mAvdManagerConnection.getInternalStorageSizeFromHardwareProperties());
  }

  public void testDoesSystemImageSupportQemu2() {
    String AVD_LOCATION = "/avd";
    String SDK_LOCATION = "/sdk";
    RepositoryPackages packages = new RepositoryPackages();

    // QEMU-1 image
    String q1Path = "system-images;android-q1;google_apis;x86";
    FakeLocalPackage q1Package = new FakeLocalPackage(q1Path, mFileOp.toPath("/sdk/mySysImg1"));
    DetailsTypes.SysImgDetailsType q1Details = AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    q1Details.getTags().add(IdDisplay.create("google_apis", "Google APIs"));
    q1Package.setTypeDetails((TypeDetails)q1Details);
    mFileOp.recordExistingFile(q1Package.getLocation().resolve(SystemImageManager.SYS_IMG_NAME));

    // QEMU-2 image
    String q2Path = "system-images;android-q2;google_apis;x86";
    FakeLocalPackage q2Package = new FakeLocalPackage(q2Path, mFileOp.toPath("/sdk/mySysImg2"));
    DetailsTypes.SysImgDetailsType q2Details = AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    q2Details.getTags().add(IdDisplay.create("google_apis", "Google APIs"));
    q2Package.setTypeDetails((TypeDetails)q2Details);
    mFileOp.recordExistingFile(q2Package.getLocation().resolve(SystemImageManager.SYS_IMG_NAME));
    // Add a file that indicates QEMU-2 support
    mFileOp.recordExistingFile(q2Package.getLocation().resolve("kernel-ranchu"));

    // QEMU-2-64 image
    String q2_64Path = "system-images;android-q2-64;google_apis;x86";
    FakeLocalPackage q2_64Package = new FakeLocalPackage(q2_64Path, mFileOp.toPath("/sdk/mySysImg3"));
    DetailsTypes.SysImgDetailsType q2_64Details = AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    q2_64Details.getTags().add(IdDisplay.create("google_apis", "Google APIs"));
    q2_64Package.setTypeDetails((TypeDetails)q2_64Details);
    mFileOp.recordExistingFile(q2_64Package.getLocation().resolve(SystemImageManager.SYS_IMG_NAME));
    // Add a file that indicates QEMU-2 support
    mFileOp.recordExistingFile(q2_64Package.getLocation().resolve("kernel-ranchu-64"));

    packages.setLocalPkgInfos(ImmutableList.of(q1Package, q2Package, q2_64Package));
    FakeRepoManager mgr = new FakeRepoManager(mFileOp.toPath(SDK_LOCATION), packages);

    AndroidSdkHandler sdkHandler =
      new AndroidSdkHandler(mFileOp.toPath(SDK_LOCATION), mFileOp.toPath(AVD_LOCATION), mFileOp, mgr);

    FakeProgressIndicator progress = new FakeProgressIndicator();
    SystemImageManager systemImageManager = sdkHandler.getSystemImageManager(progress);

    ISystemImage q1Image =
      systemImageManager.getImageAt(Objects.requireNonNull(sdkHandler.getLocalPackage(q1Path, progress)).getLocation());
    ISystemImage q2Image =
      systemImageManager.getImageAt(Objects.requireNonNull(sdkHandler.getLocalPackage(q2Path, progress)).getLocation());
    ISystemImage q2_64Image =
      systemImageManager.getImageAt(Objects.requireNonNull(sdkHandler.getLocalPackage(q2_64Path, progress)).getLocation());

    assert q1Image != null;
    SystemImageDescription q1ImageDescription = new SystemImageDescription(q1Image);
    assert q2Image != null;
    SystemImageDescription q2ImageDescription = new SystemImageDescription(q2Image);
    assert q2_64Image != null;
    SystemImageDescription q2_64ImageDescription = new SystemImageDescription(q2_64Image);

    assertFalse("Should not support QEMU2", AvdManagerConnection.doesSystemImageSupportQemu2(q1ImageDescription));
    assertTrue("Should support QEMU2", AvdManagerConnection.doesSystemImageSupportQemu2(q2ImageDescription));
    assertTrue("Should support QEMU2", AvdManagerConnection.doesSystemImageSupportQemu2(q2_64ImageDescription));
  }

  // Note: This only tests a small part of startAvd(). We are not set up
  //       here to actually launch an Emulator instance.
  public void testStartAvdSkinned() throws Exception {
    MockLog log = new MockLog();

    // Create an AVD with a skin
    String skinnyAvdName = "skinnyAvd";
    Path skinnyAvdFolder = AvdInfo.getDefaultAvdFolder(mAvdManager, skinnyAvdName, false);
    File skinFolder = new File(ANDROID_PREFS_ROOT, "skinFolder");
    mFileOp.mkdirs(skinFolder);

    AvdInfo skinnyAvd = mAvdManager.createAvd(
      skinnyAvdFolder,
      skinnyAvdName,
      mSystemImage,
      mFileOp.toPath(skinFolder),
      "skinName",
      null,
      null,
      null,
      false,
      true,
      false,
      log);

    try {
      assert skinnyAvd != null;
      mAvdManagerConnection.startAvd(null, skinnyAvd).get(4, TimeUnit.SECONDS);
      fail();
    }
    catch (ExecutionException expected) {
      assertTrue(expected.getCause().getMessage().contains("No emulator installed"));
    }
  }

  public void testFindEmulator() {
    // Create files that looks like Emulator binaries
    String binaryName = SystemInfo.isWindows ? "emulator.exe" : "emulator";
    mFileOp.recordExistingFile("/sdk/emulator/" + binaryName);
    mFileOp.recordExistingFile("/sdk/tools/" + binaryName);

    Path emulatorFile = mAvdManagerConnection.getEmulatorBinary();
    assertNotNull("Could not find Emulator", emulatorFile);
    Path emulatorDirectory = emulatorFile.getParent();
    assertTrue("Found invalid Emulator", Files.isDirectory(emulatorDirectory));
    String emulatorDirectoryPath = mFileOp.getPlatformSpecificPath(emulatorDirectory.toString());
    assertEquals("Found wrong emulator", mFileOp.getPlatformSpecificPath("/sdk/emulator"), emulatorDirectoryPath);

    // Remove the emulator package
    File emulatorPackage = new File("/sdk/emulator/package.xml");
    mFileOp.delete(emulatorPackage);

    // Create a new AvdManagerConnection that doesn't remember the
    // previous list of packages
    AndroidSdkHandler androidSdkHandler = new AndroidSdkHandler(mFileOp.toPath("/sdk"), mFileOp.toPath(ANDROID_PREFS_ROOT), mFileOp);
    AvdManagerConnection managerConnection =
      new AvdManagerConnection(androidSdkHandler, mAvdFolder, MoreExecutors.newDirectExecutorService());

    Path bogusEmulatorFile = managerConnection.getEmulatorBinary();
    if (bogusEmulatorFile != null) {
      // An emulator binary was found. It should not be anything that
      // we created (especially not anything in /sdk/tools/).
      String bogusEmulatorPath = bogusEmulatorFile.toString();
      assertFalse("Should not have found Emulator", bogusEmulatorPath.startsWith(mFileOp.getPlatformSpecificPath("/sdk")));
    }
  }

  // Note: This only tests a small part of startAvd(). We are not set up here to actually launch an Emulator instance.
  public void testStartAvdSkinless() throws Exception {
    MockLog log = new MockLog();

    // Create an AVD without a skin
    String skinlessAvdName = "skinlessAvd";
    Path skinlessAvdFolder = AvdInfo.getDefaultAvdFolder(mAvdManager, skinlessAvdName, false);

    AvdInfo skinlessAvd = mAvdManager.createAvd(
      skinlessAvdFolder,
      skinlessAvdName,
      mSystemImage,
      null,
      null,
      null,
      null,
      null,
      false,
      true,
      false,
      log);

    try {
      assert skinlessAvd != null;
      mAvdManagerConnection.startAvd(null, skinlessAvd).get(4, TimeUnit.SECONDS);
      fail();
    }
    catch (ExecutionException expected) {
      assertTrue(expected.getCause().getMessage().contains("No emulator installed"));
    }
  }

  private static void recordGoogleApisSysImg23(MockFileOp fop) {
    fop.recordExistingFile("/sdk/system-images/android-23/google_apis/x86_64/system.img");
    fop.recordExistingFile("/sdk/system-images/android-23/google_apis/x86_64/"
                           + AvdManager.USERDATA_IMG, "Some dummy info");
    fop.recordExistingFile("/sdk/system-images/android-23/google_apis/x86_64/package.xml",
                           "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                           + "<ns3:sdk-sys-img "
                           + "xmlns:ns3=\"http://schemas.android.com/sdk/android/repo/sys-img2/01\">"
                           + "<localPackage path=\"system-images;android-23;google_apis;x86_64\">"
                           + "<type-details xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                           + "xsi:type=\"ns3:sysImgDetailsType\"><api-level>23</api-level>"
                           + "<tag><id>google_apis</id><display>Google APIs</display></tag>"
                           + "<vendor><id>google</id><display>Google Inc.</display></vendor>"
                           + "<abi>x86_64</abi></type-details><revision><major>9</major></revision>"
                           + "<display-name>Google APIs Intel x86 Atom_64 System Image</display-name>"
                           + "</localPackage></ns3:sdk-sys-img>\n");
  }

  private static void recordEmulatorVersion_23_4_5(MockFileOp fop) {
    // This creates two 'package' directories.
    // We do not create a valid Emulator executable, so tests expect
    // a failure when they try to launch the Emulator.
    fop.recordExistingFile("/sdk/emulator/package.xml",
                           "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                           + "<ns2:repository xmlns:ns2=\"http://schemas.android.com/repository/android/common/01\""
                           + "                xmlns:ns3=\"http://schemas.android.com/repository/android/generic/01\">"
                           + "  <localPackage path=\"emulator\">"
                           + "    <type-details xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                           + "        xsi:type=\"ns3:genericDetailsType\"/>"
                           + "    <revision><major>23</major><minor>4</minor><micro>5</micro></revision>"
                           + "    <display-name>Google APIs Intel x86 Atom_64 System Image</display-name>"
                           + "  </localPackage>"
                           + "</ns2:repository>");
    fop.recordExistingFile("/sdk/tools/package.xml",
                           "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                           + "<ns2:repository xmlns:ns2=\"http://schemas.android.com/repository/android/common/01\""
                           + "                xmlns:ns3=\"http://schemas.android.com/repository/android/generic/01\">"
                           + "  <localPackage path=\"tools\">"
                           + "    <type-details xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                           + "        xsi:type=\"ns3:genericDetailsType\"/>"
                           + "    <revision><major>23</major><minor>4</minor><micro>5</micro></revision>"
                           + "    <display-name>Google APIs Intel x86 Atom_64 System Image</display-name>"
                           + "  </localPackage>"
                           + "</ns2:repository>");
  }

  private static void recordEmulatorHardwareProperties(MockFileOp fop) {
    fop.recordExistingFile("/sdk/emulator/lib/hardware-properties.ini",
                           "name        = sdcard.size\n"
                           + "type        = diskSize\n"
                           + "default     = 800M\n"
                           + "abstract    = SD Card Image Size\n"
                           + "# Data partition size.\n"
                           + "name        = disk.dataPartition.size\n"
                           + "type        = diskSize\n"
                           + "default     = 2G\n"
                           + "abstract    = Ideal size of data partition\n");
  }
}
