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

import com.google.common.collect.Sets;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.yarn.api.records.ContainerId;
import org.apache.hadoop.yarn.api.records.ContainerLaunchContext;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.container.Container;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.linux.privileged.PrivilegedOperation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NvidiaGpuResourceHandlerImpl implements ResourceHandler {
  final static Log LOG = LogFactory
      .getLog(NvidiaGpuResourceHandlerImpl.class);

  private final String REQUEST_GPU_NUM_ENV_KEY = "REQUESTED_GPU_NUM";
  private Set<Integer> allowedGpuDevices = new HashSet<>();
  private Map<Integer, ContainerId> usedDevices = new HashMap<>();
  private CGroupsHandler cGroupsHandler;

  NvidiaGpuResourceHandlerImpl(CGroupsHandler cGroupsHandler) {
    this.cGroupsHandler = cGroupsHandler;
  }

  @Override
  public List<PrivilegedOperation> bootstrap(Configuration configuration)
      throws ResourceHandlerException {
    String allowedDevicesStr = configuration.get(
        YarnConfiguration.NM_NVIDIA_GPU_ALLOWED_DEVICES);

    if (null != allowedDevicesStr) {
      for (String s : allowedDevicesStr.split(",")) {
        Integer minorNum = Integer.valueOf(s);
        allowedGpuDevices.add(minorNum);
      }
    }
    LOG.info("Allocated GPU devices minor numbers:" + allowedDevicesStr);

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
    // Always remove NV_GPU from environment
    container.getLaunchContext().getEnvironment().remove("NV_GPU");

    ContainerId containerId = container.getContainerId();

    int requestedGpu = getRequestedGpu(container);

    StringBuilder nvGpuEnv = new StringBuilder();

    Set<Integer> assignedGpus = new HashSet<>();

    // Assign Gpus to container if requested some.
    if (requestedGpu > 0) {
      for (int deviceNum : allowedGpuDevices) {
        if (!usedDevices.containsKey(deviceNum)) {
          usedDevices.put(deviceNum, containerId);
          assignedGpus.add(deviceNum);
          if (nvGpuEnv.length() > 0) {
            nvGpuEnv.append(",");
          }
          nvGpuEnv.append(deviceNum);
          if (assignedGpus.size() >= requestedGpu) {
            Map<String, String> envs =
                container.getLaunchContext().getEnvironment();
            envs.put("NV_GPU", nvGpuEnv.toString());
            LOG.info("Assigned GPU=" + nvGpuEnv.toString() + " to container="
                + containerId);
            break;
          }
        }
      }
    }

    // Make sure we have enough Gpu assigned.
    if (requestedGpu > assignedGpus.size()) {
      // Release all assigned Gpus
      releaseAssignedGpusForContainer(containerId);

      throw new ResourceHandlerException(
          "Failed to find enough GPU to assign, requested=" + requestedGpu
              + ", availble=" + (allowedGpuDevices.size() - usedDevices.size())
              + " for container=" + container.getContainerId());
    }

    // Get Gpu blacklists
    Set<Integer> deniedGpus = Sets.difference(allowedGpuDevices, assignedGpus);
    cGroupsHandler.createCGroup(CGroupsHandler.CGroupController.DEVICES,
        containerId.toString());
    try {
      for (int device : deniedGpus) {
        cGroupsHandler.updateCGroupParam(CGroupsHandler.CGroupController.DEVICES,
            containerId.toString(), CGroupsHandler.CGROUP_PARAM_DEVICE_DENY,
            getDeviceDeniedValue(device));
      }
    } catch (ResourceHandlerException re) {
      cGroupsHandler.deleteCGroup(CGroupsHandler.CGroupController.DEVICES,
          containerId.toString());
      LOG.warn("Could not update cgroup for container", re);
      throw re;
    }

    List<PrivilegedOperation> ret = new ArrayList<>();
    ret.add(new PrivilegedOperation(
        PrivilegedOperation.OperationType.ADD_PID_TO_CGROUP,
        PrivilegedOperation.CGROUP_ARG_PREFIX
            + cGroupsHandler.getPathForCGroupTasks(
            CGroupsHandler.CGroupController.DEVICES, containerId.toString())));

    return ret;
  }

  private String getDeviceDeniedValue(int device) {
    String val = String.format("c 195:%d rwm", device);
    LOG.info("Add denied devices to cgroups:" + val);
    return val;
  }

  @Override
  public List<PrivilegedOperation> reacquireContainer(ContainerId containerId)
      throws ResourceHandlerException {
    return null;
  }

  private void releaseAssignedGpusForContainer(ContainerId containerId) {
    Iterator<Map.Entry<Integer, ContainerId>> iter =
        usedDevices.entrySet().iterator();
    while (iter.hasNext()) {
      if (iter.next().getValue().equals(containerId)) {
        iter.remove();
      }
    }
  }

  @Override
  public synchronized List<PrivilegedOperation> postComplete(
      ContainerId containerId) throws ResourceHandlerException {
    releaseAssignedGpusForContainer(containerId);
    cGroupsHandler.deleteCGroup(CGroupsHandler.CGroupController.DEVICES,
        containerId.toString());
    return null;
  }

  @Override
  public List<PrivilegedOperation> teardown() throws ResourceHandlerException {
    return null;
  }
}
