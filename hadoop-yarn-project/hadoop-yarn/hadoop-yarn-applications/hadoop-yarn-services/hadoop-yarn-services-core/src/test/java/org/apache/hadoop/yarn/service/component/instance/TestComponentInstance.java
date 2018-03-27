/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.yarn.service.component.instance;

import org.apache.hadoop.yarn.api.records.ApplicationAttemptId;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.ContainerId;
import org.apache.hadoop.yarn.api.records.ContainerState;
import org.apache.hadoop.yarn.api.records.ContainerStatus;
import org.apache.hadoop.yarn.service.ServiceScheduler;
import org.apache.hadoop.yarn.service.component.Component;
import org.apache.hadoop.yarn.service.utils.ServiceUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestComponentInstance {
  private Component createComponent(ServiceScheduler scheduler,
      org.apache.hadoop.yarn.service.api.records.Component.RestartPolicyEnum restartPolicy,
      int nSucceededInstances, int nFailedInstances, int totalAsk) {
    Component comp = mock(Component.class);
    org.apache.hadoop.yarn.service.api.records.Component componentSpec = mock(
        org.apache.hadoop.yarn.service.api.records.Component.class);
    when(componentSpec.getRestartPolicy()).thenReturn(restartPolicy);
    when(componentSpec.getNumberOfContainers()).thenReturn(
        Long.valueOf(totalAsk));
    when(comp.getComponentSpec()).thenReturn(componentSpec);
    AtomicInteger succeeded = new AtomicInteger(nSucceededInstances);
    AtomicInteger failed = new AtomicInteger(nFailedInstances);
    when(comp.getSuceededInstances()).thenReturn(succeeded);
    when(comp.getFailedInstances()).thenReturn(failed);
    when(comp.getScheduler()).thenReturn(scheduler);
    return comp;
  }

  private ComponentInstance createComponentInstance(
      org.apache.hadoop.yarn.service.api.records.Component.RestartPolicyEnum restartPolicy,
      int nSucceededInstances, int nFailedInstances, int totalAsk) {
    // clean up the mock every time.
    ComponentInstance.terminationHandler = mock(
        ServiceUtils.ProcessTerminationHandler.class);

    ServiceScheduler serviceScheduler = mock(ServiceScheduler.class);
    Component comp = createComponent(serviceScheduler, restartPolicy,
        nSucceededInstances, nFailedInstances, totalAsk);

    ComponentInstance componentInstance = mock(ComponentInstance.class);
    when(componentInstance.getComponent()).thenReturn(comp);

    Map<String, Component> allComponents = new HashMap<>();
    allComponents.put("comp1", comp);

    when(serviceScheduler.getAllComponents()).thenReturn(allComponents);

    return componentInstance;
  }

  @Test
  public void testComponentRestartPolicy() {
    ComponentInstanceEvent componentInstanceEvent = mock(
        ComponentInstanceEvent.class);
    ContainerId containerId = ContainerId.newContainerId(ApplicationAttemptId
        .newInstance(ApplicationId.newInstance(1234L, 1), 1), 1);
    ContainerStatus containerStatus = ContainerStatus.newInstance(containerId,
        ContainerState.COMPLETE, "hello", 0);

    when(componentInstanceEvent.getStatus()).thenReturn(containerStatus);

    // Test case1: one component, one instance, restart policy = ALWAYS, exit=0
    ComponentInstance componentInstance = createComponentInstance(
        org.apache.hadoop.yarn.service.api.records.Component.RestartPolicyEnum.ALWAYS,
        0, 0, 1);
    ComponentInstance.handleComponentInstanceRelaunch(componentInstance,
        componentInstanceEvent);
    Assert.assertEquals(0,
        componentInstance.getComponent().getFailedInstances().get());
    Assert.assertEquals(0,
        componentInstance.getComponent().getSuceededInstances().get());
    verify(componentInstance.getComponent(), times(1)).reInsertPendingInstance(
        any(ComponentInstance.class));
    verify(ComponentInstance.terminationHandler, never()).terminate(anyInt());

    // Test case2: one component, one instance, restart policy = ALWAYS, exit=1
    componentInstance = createComponentInstance(
        org.apache.hadoop.yarn.service.api.records.Component.RestartPolicyEnum.ALWAYS,
        0, 0, 1);
    containerStatus.setExitStatus(1);
    ComponentInstance.handleComponentInstanceRelaunch(componentInstance,
        componentInstanceEvent);
    Assert.assertEquals(0,
        componentInstance.getComponent().getFailedInstances().get());
    Assert.assertEquals(0,
        componentInstance.getComponent().getSuceededInstances().get());
    verify(componentInstance.getComponent(), times(1)).reInsertPendingInstance(
        any(ComponentInstance.class));
    verify(ComponentInstance.terminationHandler, never()).terminate(anyInt());

    // Test case3: one component, one instance, restart policy = NEVER, exit=0
    // Should exit with code=0
    componentInstance = createComponentInstance(
        org.apache.hadoop.yarn.service.api.records.Component.RestartPolicyEnum.NEVER,
        0, 0, 1);
    containerStatus.setExitStatus(0);
    ComponentInstance.handleComponentInstanceRelaunch(componentInstance,
        componentInstanceEvent);
    Assert.assertEquals(0,
        componentInstance.getComponent().getFailedInstances().get());
    Assert.assertEquals(1,
        componentInstance.getComponent().getSuceededInstances().get());
    verify(componentInstance.getComponent(), times(0)).reInsertPendingInstance(
        any(ComponentInstance.class));
    verify(ComponentInstance.terminationHandler, times(1)).terminate(eq(0));

    // Test case4: one component, one instance, restart policy = NEVER, exit=1
    // Should exit with code=-1
    componentInstance = createComponentInstance(
        org.apache.hadoop.yarn.service.api.records.Component.RestartPolicyEnum.NEVER,
        0, 0, 1);
    containerStatus.setExitStatus(1);
    ComponentInstance.handleComponentInstanceRelaunch(componentInstance,
        componentInstanceEvent);
    Assert.assertEquals(1,
        componentInstance.getComponent().getFailedInstances().get());
    Assert.assertEquals(0,
        componentInstance.getComponent().getSuceededInstances().get());
    verify(componentInstance.getComponent(), times(0)).reInsertPendingInstance(
        any(ComponentInstance.class));
    verify(ComponentInstance.terminationHandler, times(1)).terminate(eq(-1));

    // Test case5: one component, one instance, restart policy = ON_FAILURE, exit=1
    // Should continue run.
    componentInstance = createComponentInstance(
        org.apache.hadoop.yarn.service.api.records.Component.RestartPolicyEnum.ON_FAILURE,
        0, 0, 1);
    containerStatus.setExitStatus(1);
    ComponentInstance.handleComponentInstanceRelaunch(componentInstance,
        componentInstanceEvent);
    Assert.assertEquals(0,
        componentInstance.getComponent().getFailedInstances().get());
    Assert.assertEquals(0,
        componentInstance.getComponent().getSuceededInstances().get());
    verify(componentInstance.getComponent(), times(1)).reInsertPendingInstance(
        any(ComponentInstance.class));
    verify(ComponentInstance.terminationHandler, times(0)).terminate(anyInt());

    // Test case6: one component, 3 instances, restart policy = NEVER, exit=1
    // 2 of the components completed, it should continue run.
    componentInstance = createComponentInstance(
        org.apache.hadoop.yarn.service.api.records.Component.RestartPolicyEnum.NEVER,
        1, 0, 3);
    containerStatus.setExitStatus(1);
    ComponentInstance.handleComponentInstanceRelaunch(componentInstance,
        componentInstanceEvent);
    Assert.assertEquals(1,
        componentInstance.getComponent().getFailedInstances().get());
    Assert.assertEquals(1,
        componentInstance.getComponent().getSuceededInstances().get());
    verify(componentInstance.getComponent(), times(0)).reInsertPendingInstance(
        any(ComponentInstance.class));
    verify(ComponentInstance.terminationHandler, times(0)).terminate(anyInt());

    // Test case7: one component, 3 instances, restart policy = ON_FAILURE, exit=1
    // 2 of the components completed, it should continue run.
    componentInstance = createComponentInstance(
        org.apache.hadoop.yarn.service.api.records.Component.RestartPolicyEnum.ON_FAILURE,
        1, 0, 3);
    containerStatus.setExitStatus(1);
    ComponentInstance.handleComponentInstanceRelaunch(componentInstance,
        componentInstanceEvent);
    Assert.assertEquals(0,
        componentInstance.getComponent().getFailedInstances().get());
    Assert.assertEquals(1,
        componentInstance.getComponent().getSuceededInstances().get());
    verify(componentInstance.getComponent(), times(1)).reInsertPendingInstance(
        any(ComponentInstance.class));
    verify(ComponentInstance.terminationHandler, times(0)).terminate(anyInt());

    // Test case8: 2 components, 2 instances for each
    // comp2 already finished.
    // comp1 has a new instance finish, we should terminate the service
    componentInstance = createComponentInstance(
        org.apache.hadoop.yarn.service.api.records.Component.RestartPolicyEnum.NEVER,
        1, 0, 2);
    containerStatus.setExitStatus(1);
    // 2nd component, already finished.
    Component comp2 = createComponent(
        componentInstance.getComponent().getScheduler(),
        org.apache.hadoop.yarn.service.api.records.Component.RestartPolicyEnum.NEVER,
        1, 1, 2);
    componentInstance.getComponent().getScheduler().getAllComponents().put(
        "comp2", comp2);

    ComponentInstance.handleComponentInstanceRelaunch(componentInstance,
        componentInstanceEvent);
    Assert.assertEquals(1,
        componentInstance.getComponent().getFailedInstances().get());
    Assert.assertEquals(1,
        componentInstance.getComponent().getSuceededInstances().get());
    verify(componentInstance.getComponent(), times(0)).reInsertPendingInstance(
        any(ComponentInstance.class));
    verify(ComponentInstance.terminationHandler, times(1)).terminate(eq(-1));

    // Test case9: 2 components, 2 instances for each
    // comp2 already finished.
    // comp1 has a new instance finish, we should terminate the service
    // All instance finish with 0, service should exit with 0 as well.
    componentInstance = createComponentInstance(
        org.apache.hadoop.yarn.service.api.records.Component.RestartPolicyEnum.NEVER,
        1, 0, 2);
    containerStatus.setExitStatus(0);
    // 2nd component, already finished.
    comp2 = createComponent(
        componentInstance.getComponent().getScheduler(),
        org.apache.hadoop.yarn.service.api.records.Component.RestartPolicyEnum.NEVER,
        2, 0, 2);
    componentInstance.getComponent().getScheduler().getAllComponents().put(
        "comp2", comp2);

    ComponentInstance.handleComponentInstanceRelaunch(componentInstance,
        componentInstanceEvent);
    Assert.assertEquals(0,
        componentInstance.getComponent().getFailedInstances().get());
    Assert.assertEquals(2,
        componentInstance.getComponent().getSuceededInstances().get());
    verify(componentInstance.getComponent(), times(0)).reInsertPendingInstance(
        any(ComponentInstance.class));
    verify(ComponentInstance.terminationHandler, times(1)).terminate(eq(0));

    // Test case10: 2 components, 2 instances for each
    // comp2 hasn't finished
    // comp1 finished.
    // Service should continue run.
    componentInstance = createComponentInstance(
        org.apache.hadoop.yarn.service.api.records.Component.RestartPolicyEnum.NEVER,
        1, 0, 2);
    containerStatus.setExitStatus(0);
    // 2nd component, already finished.
    comp2 = createComponent(
        componentInstance.getComponent().getScheduler(),
        org.apache.hadoop.yarn.service.api.records.Component.RestartPolicyEnum.NEVER,
        1, 0, 2);
    componentInstance.getComponent().getScheduler().getAllComponents().put(
        "comp2", comp2);

    ComponentInstance.handleComponentInstanceRelaunch(componentInstance,
        componentInstanceEvent);
    Assert.assertEquals(0,
        componentInstance.getComponent().getFailedInstances().get());
    Assert.assertEquals(2,
        componentInstance.getComponent().getSuceededInstances().get());
    verify(componentInstance.getComponent(), times(0)).reInsertPendingInstance(
        any(ComponentInstance.class));
    verify(ComponentInstance.terminationHandler, times(0)).terminate(anyInt());
  }

}
