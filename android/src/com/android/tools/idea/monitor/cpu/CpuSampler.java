/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.monitor.cpu;

import com.android.ddmlib.*;
import com.android.tools.idea.monitor.DeviceSampler;
import com.android.tools.idea.monitor.TimelineData;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class CpuSampler extends DeviceSampler {
  /**
   * The device is reachable but no cpu usage response was received in time.
   */
  public static final int TYPE_ERROR = INHERITED_TYPE_START;

  public static final int TYPE_NOT_FOUND = INHERITED_TYPE_START + 1;

  private static final Logger LOG = Logger.getInstance(CpuSampler.class);

  private Long previousKernelUsage = null;
  private Long previousUserUsage = null;
  private Long previousTotalUptime = null;

  /**
   * Output receiver for contents in the "/proc/[pid]/stat" pseudo file.
   */
  static final class ProcessStatReceiver extends MultiLineReceiver {
    private final int myPid;
    private Long myUserCpuTicks;
    private Long myKernelCpuTicks;

    private ProcessStatReceiver(int pid) {
      myPid = pid;
    }

    /**
     * Get the parsed user space CPU usage.
     *
     * @return user cpu usage or <code>null</code> if it cannot be determined
     */
    @Nullable
    public Long getUserCpuUsage() {
      return myUserCpuTicks;
    }

    /**
     * Get the parsed kernel space CPU usage.
     *
     * @return kernel cpu usage or <code>null</code> if it cannot be determined
     */
    @Nullable
    public Long getKernelCpuUsage() {
      return myKernelCpuTicks;
    }

    @Override
    public boolean isCancelled() {
      return false;
    }

    @Override
    public void processNewLines(@NotNull String[] lines) {
      String[] tokens = lines[0].split("\\s+");
      if (tokens.length >= 15) {
        // Refer to Linux proc man page for the contents at the specified indices.
        Integer pid = Integer.parseInt(tokens[0]);
        if (pid != myPid) {
          LOG.warn("Invalid pid.");
          return;
        }

        myUserCpuTicks = Long.parseLong(tokens[13]);
        myKernelCpuTicks = Long.parseLong(tokens[14]);
      }
    }
  }

  /**
   * Output receiver for contents in the "/proc/stat" pseudo file.
   */
  static final class SystemStatReceiver extends MultiLineReceiver {
    private Long myTotalUptime = null;

    public Long getTotalUptime() {
      return myTotalUptime;
    }

    @Override
    public boolean isCancelled() {
      return false;
    }

    @Override
    public void processNewLines(String[] lines) {
      long totalUptime = 0l;

      String[] tokens = lines[0].split("\\s+");
      if (tokens.length < 11 || !tokens[0].equals("cpu")) {
        return;
      }

      // Assuming total uptime is the sum of all given numerical values on the aggregated CPU line.
      for (int i = 1; i < tokens.length; ++i) {
        totalUptime += Long.parseLong(tokens[i]);
      }
      myTotalUptime = totalUptime;
    }
  }

  public CpuSampler(@NotNull TimelineData data, int sampleFrequencyMs) {
    super(data, sampleFrequencyMs);
  }

  @NotNull
  @Override
  public String getName() {
    return "CPU Sampler";
  }

  @NotNull
  @Override
  public String getDescription() {
    return "cpu usage information";
  }

  @Override
  protected void sample(int type, int id) {
    if (myClient == null) {
      return;
    }

    //noinspection ConstantConditions
    IDevice device = myClient.getDevice();
    //noinspection ConstantConditions
    ClientData data = myClient.getClientData();

    Long kernelCpuUsage = null;
    Long userCpuUsage = null;
    Long totalUptime = null;

    if (device != null) {
      try {
        int pid = data.getPid();
        ProcessStatReceiver dumpsysReceiver = new ProcessStatReceiver(pid);
        device.executeShellCommand("cat /proc/" + pid + "/stat", dumpsysReceiver, 1, TimeUnit.SECONDS);
        kernelCpuUsage = dumpsysReceiver.getKernelCpuUsage();
        userCpuUsage = dumpsysReceiver.getUserCpuUsage();

        SystemStatReceiver systemStatReceiver = new SystemStatReceiver();
        device.executeShellCommand("cat /proc/stat", systemStatReceiver, 1, TimeUnit.SECONDS);
        totalUptime = systemStatReceiver.getTotalUptime();
      }
      catch (TimeoutException e) {
        LOG.info(e);
        type = TYPE_TIMEOUT;
      }
      catch (AdbCommandRejectedException e) {
        LOG.warn(e);
        type = TYPE_ERROR;
      }
      catch (ShellCommandUnresponsiveException e) {
        LOG.warn(e);
        type = TYPE_UNREACHABLE;
      }
      catch (IOException e) {
        LOG.warn(e);
        type = TYPE_UNREACHABLE;
      }
    }

    if (kernelCpuUsage != null && userCpuUsage != null && totalUptime != null) {
      if (previousKernelUsage != null && previousUserUsage != null && previousTotalUptime != null) {
        long totalTimeDiff = totalUptime - previousTotalUptime;
        if (totalTimeDiff > 0) {
          myData.add(System.currentTimeMillis(), type, id, (float)(kernelCpuUsage - previousKernelUsage) * 100.0f / (float)totalTimeDiff,
                     (float)(userCpuUsage - previousUserUsage) * 100.0f / (float)totalTimeDiff);
        }
      }
      previousKernelUsage = kernelCpuUsage;
      previousUserUsage = userCpuUsage;
      previousTotalUptime = totalUptime;
    }
    else {
      myData.add(System.currentTimeMillis(), TYPE_NOT_FOUND, id, 0.0f, 0.0f);
    }
  }

  @Override
  protected void sampleTimeoutHandler() {

  }
}
