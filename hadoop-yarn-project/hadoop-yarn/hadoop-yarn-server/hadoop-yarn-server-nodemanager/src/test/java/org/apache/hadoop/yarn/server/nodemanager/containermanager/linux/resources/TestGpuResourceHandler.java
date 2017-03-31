package org.apache.hadoop.yarn.server.nodemanager.containermanager.linux.resources;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.yarn.api.records.ApplicationAttemptId;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.ContainerId;
import org.apache.hadoop.yarn.api.records.ContainerLaunchContext;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.container.Container;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestGpuResourceHandler {
  private CGroupsHandler mockCGroupsHandler;
  private GpuResourceHandlerImpl gpuResourceHandler;

  @Before
  public void setup() {
    mockCGroupsHandler = mock(CGroupsHandler.class);
    gpuResourceHandler = new GpuResourceHandlerImpl(
        mockCGroupsHandler);
  }

  @Test
  public void testBootStrap() throws ResourceHandlerException {
    Configuration conf = new YarnConfiguration();

    gpuResourceHandler.bootstrap(conf);
    verify(mockCGroupsHandler, times(1)).initializeCGroupController(
        CGroupsHandler.CGroupController.DEVICES);
  }

  private static ContainerId getContainerId(int id) {
    return ContainerId.newContainerId(ApplicationAttemptId
        .newInstance(ApplicationId.newInstance(1234L, 1), 1), id);
  }

  private static Container mockContainer(int id, int numGpuRequest) {
    Container c = mock(Container.class);
    when(c.getContainerId()).thenReturn(getContainerId(id));
    ContainerLaunchContext clc = mock(ContainerLaunchContext.class);
    Map<String, String> envs = new HashMap<>();
    when(clc.getEnvironment()).thenReturn(envs);
    envs.put("REQUESTED_GPU_NUM", String.valueOf(numGpuRequest));
    when(c.getLaunchContext()).thenReturn(clc);
    return c;
  }

  private void verifyDeniedDevices(ContainerId containerId, int[] deniedDevices)
      throws ResourceHandlerException {
    verify(mockCGroupsHandler, times(1)).createCGroup(
        CGroupsHandler.CGroupController.DEVICES, containerId.toString());

    if (null != deniedDevices && deniedDevices.length > 0) {
      for (int i = 0; i < deniedDevices.length; i++) {
        verify(mockCGroupsHandler, times(1)).updateCGroupParam(
            CGroupsHandler.CGroupController.DEVICES, containerId.toString(),
            CGroupsHandler.CGROUP_PARAM_DEVICE_DENY,
            GpuResourceHandlerImpl
                .getDeviceDeniedValue(deniedDevices[i]));
      }
    }
  }

  @Test
  public void testAllocation() throws Exception {
    Configuration conf = new YarnConfiguration();
    conf.set(YarnConfiguration.NM_GPU_ALLOWED_DEVICES, "0,1,3,4");
    conf.setBoolean(YarnConfiguration.NM_GPU_RESOURCE_ENABLED, true);

    gpuResourceHandler.bootstrap(conf);
    Assert.assertEquals(4,
        gpuResourceHandler.getGpuAllocator().getAvailableGpus());

    /* Start container 1, asks 3 containers */
    gpuResourceHandler.preStart(mockContainer(1, 3));

    // Only device=4 will be blocked.
    verifyDeniedDevices(getContainerId(1), new int[] { 4 });

    /* Start container 2, asks 2 containers. Excepted to fail */
    boolean failedToAllocate = false;
    try {
      gpuResourceHandler.preStart(mockContainer(2, 2));
    } catch (ResourceHandlerException e) {
      failedToAllocate = true;
    }
    Assert.assertTrue(failedToAllocate);

    /* Start container 3, ask 1 container, succeeded */
    gpuResourceHandler.preStart(mockContainer(3, 1));

    // devices = 0/1/3 will be blocked
    verifyDeniedDevices(getContainerId(3), new int[] { 0, 1, 3 });

    /* Start container 4, ask 0 container, succeeded */
    gpuResourceHandler.preStart(mockContainer(4, 0));

    // All devices will be blocked
    verifyDeniedDevices(getContainerId(4), new int[] { 0, 1, 3, 4 });

    /* Release container-1, expect cgroups deleted */
    gpuResourceHandler.postComplete(getContainerId(1));

    verify(mockCGroupsHandler, times(1)).createCGroup(
        CGroupsHandler.CGroupController.DEVICES, getContainerId(1).toString());
    Assert.assertEquals(3,
        gpuResourceHandler.getGpuAllocator().getAvailableGpus());

    /* Release container-3, expect cgroups deleted */
    gpuResourceHandler.postComplete(getContainerId(3));

    verify(mockCGroupsHandler, times(1)).createCGroup(
        CGroupsHandler.CGroupController.DEVICES, getContainerId(3).toString());
    Assert.assertEquals(4,
        gpuResourceHandler.getGpuAllocator().getAvailableGpus());
  }

  @Test
  public void testAllocationWithoutAllowedGpus() throws Exception {
    Configuration conf = new YarnConfiguration();
    conf.set(YarnConfiguration.NM_GPU_ALLOWED_DEVICES, "");
    conf.setBoolean(YarnConfiguration.NM_GPU_RESOURCE_ENABLED, true);

    gpuResourceHandler.bootstrap(conf);
    Assert.assertEquals(0,
        gpuResourceHandler.getGpuAllocator().getAvailableGpus());

    /* Start container 1, asks 0 containers */
    gpuResourceHandler.preStart(mockContainer(1, 0));
    verifyDeniedDevices(getContainerId(1), new int[] { });

    /* Start container 2, asks 1 containers. Excepted to fail */
    boolean failedToAllocate = false;
    try {
      gpuResourceHandler.preStart(mockContainer(2, 1));
    } catch (ResourceHandlerException e) {
      failedToAllocate = true;
    }
    Assert.assertTrue(failedToAllocate);

    /* Release container 1, expect cgroups deleted */
    gpuResourceHandler.postComplete(getContainerId(1));

    verify(mockCGroupsHandler, times(1)).createCGroup(
        CGroupsHandler.CGroupController.DEVICES, getContainerId(1).toString());
    Assert.assertEquals(0,
        gpuResourceHandler.getGpuAllocator().getAvailableGpus());
  }
}
