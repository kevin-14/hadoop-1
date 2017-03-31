/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.yarn.server.nodemanager.containermanager.linux.resources;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.util.StringUtils;
import org.apache.hadoop.yarn.api.records.ContainerId;
import org.apache.hadoop.yarn.api.records.ContainerLaunchContext;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.container.Container;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.linux.privileged.PrivilegedOperation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GpuResourceHandlerImpl implements ResourceHandler {
  final static Log LOG = LogFactory
      .getLog(GpuResourceHandlerImpl.class);

  private final String REQUEST_GPU_NUM_ENV_KEY = "REQUESTED_GPU_NUM";

  // This will be used by container-executor to add necessary clis
  public static final String ALLOCATED_GPU_MINOR_NUMS_ENV_KEY =
      "YARN_ALLOCATED_GPUS";

  private GpuResourceAllocator gpuAllocator;
  private CGroupsHandler cGroupsHandler;

  GpuResourceHandlerImpl(CGroupsHandler cGroupsHandler) {
    this.cGroupsHandler = cGroupsHandler;
    gpuAllocator = new GpuResourceAllocator();
  }

  @Override
  public List<PrivilegedOperation> bootstrap(Configuration configuration)
      throws ResourceHandlerException {
    String allowedDevicesStr = configuration.get(
        YarnConfiguration.NM_GPU_ALLOWED_DEVICES);

    if (null != allowedDevicesStr) {
      for (String s : allowedDevicesStr.split(",")) {
        if (s.trim().length() > 0) {
          Integer minorNum = Integer.valueOf(s.trim());
          gpuAllocator.addGpu(minorNum);
        }
      }
    }
    LOG.info("Allowed GPU devices with minor numbers:" + allowedDevicesStr);

    // And initialize cgroups
    this.cGroupsHandler.initializeCGroupController(
        CGroupsHandler.CGroupController.DEVICES);

    return null;
  }

  private int getRequestedGpu(Container container) {
    // TODO, use YARN-3926 after it merged
    ContainerLaunchContext clc = container.getLaunchContext();
    Map<String, String> envs = clc.getEnvironment();
    if (null != envs.get(REQUEST_GPU_NUM_ENV_KEY)) {
      return Integer.valueOf(envs.get(REQUEST_GPU_NUM_ENV_KEY));
    }
    return 0;
  }

  @Override
  public synchronized List<PrivilegedOperation> preStart(Container container)
      throws ResourceHandlerException {
    String containerIdStr = container.getContainerId().toString();

    int requestedGpu = getRequestedGpu(container);

    // Assign Gpus to container if requested some.
    GpuResourceAllocator.GpuAllocation allocation = gpuAllocator.assignGpus(
        requestedGpu, containerIdStr);

    // Create device cgroups for the container
    cGroupsHandler.createCGroup(CGroupsHandler.CGroupController.DEVICES,
        containerIdStr);
    try {
      for (int device : allocation.getDenied()) {
        cGroupsHandler.updateCGroupParam(
            CGroupsHandler.CGroupController.DEVICES, containerIdStr,
            CGroupsHandler.CGROUP_PARAM_DEVICE_DENY,
            getDeviceDeniedValue(device));
      }

      // Set ALLOCATED_GPU_MINOR_NUMS_ENV_KEY to environment so later runtime
      // can use it.
      Map<String, String> envs = container.getLaunchContext().getEnvironment();
      if (null == allocation.getAllowed() || allocation.getAllowed().isEmpty()) {
        envs.put(ALLOCATED_GPU_MINOR_NUMS_ENV_KEY, "");
      } else {
        envs.put(ALLOCATED_GPU_MINOR_NUMS_ENV_KEY,
            StringUtils.join(",", allocation.getAllowed()));
      }
    } catch (ResourceHandlerException re) {
      cGroupsHandler.deleteCGroup(CGroupsHandler.CGroupController.DEVICES,
          containerIdStr);
      LOG.warn("Could not update cgroup for container", re);
      throw re;
    }

    List<PrivilegedOperation> ret = new ArrayList<>();
    ret.add(new PrivilegedOperation(
        PrivilegedOperation.OperationType.ADD_PID_TO_CGROUP,
        PrivilegedOperation.CGROUP_ARG_PREFIX
            + cGroupsHandler.getPathForCGroupTasks(
            CGroupsHandler.CGroupController.DEVICES, containerIdStr)));

    return ret;
  }

  @VisibleForTesting
  public static String getDeviceDeniedValue(int deviceMinorNumber) {
    String val = String.format("c 195:%d rwm", deviceMinorNumber);
    LOG.info("Add denied devices to cgroups:" + val);
    return val;
  }

  @VisibleForTesting
  public GpuResourceAllocator getGpuAllocator() {
    return gpuAllocator;
  }

  @Override
  public List<PrivilegedOperation> reacquireContainer(ContainerId containerId)
      throws ResourceHandlerException {
    // FIXME, need to make sure allocated containers and cgroups can be
    // recovered when NM restarts.
    return null;
  }

  @Override
  public synchronized List<PrivilegedOperation> postComplete(
      ContainerId containerId) throws ResourceHandlerException {
    gpuAllocator.cleanupAssignGpus(containerId.toString());
    cGroupsHandler.deleteCGroup(CGroupsHandler.CGroupController.DEVICES,
        containerId.toString());
    return null;
  }

  @Override
  public List<PrivilegedOperation> teardown() throws ResourceHandlerException {
    return null;
  }
}
