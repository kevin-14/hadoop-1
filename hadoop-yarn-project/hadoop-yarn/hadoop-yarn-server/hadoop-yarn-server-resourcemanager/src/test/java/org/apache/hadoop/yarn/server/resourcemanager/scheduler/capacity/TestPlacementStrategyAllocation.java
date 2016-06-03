package org.apache.hadoop.yarn.server.resourcemanager.scheduler.capacity;

import org.apache.hadoop.yarn.api.records.Priority;
import org.apache.hadoop.yarn.api.records.ResourceRequest;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.server.resourcemanager.MockAM;
import org.apache.hadoop.yarn.server.resourcemanager.MockNM;
import org.apache.hadoop.yarn.server.resourcemanager.MockRM;
import org.apache.hadoop.yarn.server.resourcemanager.nodelabels.NullRMNodeLabelsManager;
import org.apache.hadoop.yarn.server.resourcemanager.nodelabels.RMNodeLabelsManager;
import org.apache.hadoop.yarn.server.resourcemanager.rmapp.RMApp;
import org.apache.hadoop.yarn.server.resourcemanager.rmcontainer.RMContainer;
import org.apache.hadoop.yarn.server.resourcemanager.rmnode.RMNode;
import org.apache.hadoop.yarn.server.resourcemanager.scheduler.ResourceScheduler;
import org.apache.hadoop.yarn.server.resourcemanager.scheduler.SchedulerNode;
import org.apache.hadoop.yarn.server.resourcemanager.scheduler.common.fica.FiCaSchedulerApp;
import org.apache.hadoop.yarn.server.resourcemanager.scheduler.event.NodeUpdateSchedulerEvent;
import org.apache.hadoop.yarn.util.resource.Resources;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TestPlacementStrategyAllocation {
  private final int GB = 1024;

  private YarnConfiguration conf;

  RMNodeLabelsManager mgr;

  @Before
  public void setUp() throws Exception {
    conf = new YarnConfiguration();
    conf.setClass(YarnConfiguration.RM_SCHEDULER, CapacityScheduler.class,
        ResourceScheduler.class);
    conf.setBoolean(CapacitySchedulerConfiguration.ENABLE_GLOBAL_SCHEDULING,
        true);
    mgr = new NullRMNodeLabelsManager();
    mgr.init(conf);
  }

  private List<MockNM> initNMs(int nNM, MockRM rm) throws Exception {
    List<MockNM> nms = new ArrayList<>();
    for (int i = 1; i < nNM; i++) {
      MockNM nm = rm.registerNode("h-" + i + ":1234", 8000);
      nms.add(nm);
    }
    return nms;
  }

  @Test(timeout = 300000)
  public void testAffinityAllocationBetweenApps() throws Exception {
    final RMNodeLabelsManager mgr = new NullRMNodeLabelsManager();
    mgr.init(conf);

    MockRM rm = new MockRM(conf);
    rm.start();
    List<MockNM> nms = initNMs(100, rm);

    // launch an app to queue a1 (label = x), and check all container will
    // be allocated in h1
    RMApp app1 = rm.submitApp(200, "app", "user", null, "default");
    MockAM am1 = MockRM.launchAndRegisterAM(app1, rm, nms.get(0));

    am1.allocate("*", 1024, 80, new ArrayList<>(), "");
    CapacityScheduler cs = (CapacityScheduler) rm.getResourceScheduler();

    // Do node heartbeats 2 times for each node
    for (int i = 0; i < 2; i++) {
      for (MockNM nm : nms) {
        RMNode rmNode = rm.getRMContext().getRMNodes().get(nm.getNodeId());
        cs.handle(new NodeUpdateSchedulerEvent(rmNode));
      }
    }

    // We should have 81 containers allocated for am1
    FiCaSchedulerApp schedulerApp1 = cs.getApplicationAttempt(
        am1.getApplicationAttemptId());
    Assert.assertEquals(81, schedulerApp1.getLiveContainers().size());

    // Now we submit an app2
    RMApp app2 = rm.submitApp(200, "app", "user", null, "default");
    MockAM am2 = MockRM.launchAndRegisterAM(app2, rm, nms.get(0));

    ResourceRequest rr = ResourceRequest.newInstance(Priority.newInstance(0), "*",
        Resources.createResource(1024), 100);
    rr.setPlacementStrategy(String
        .format("op=%s,targets=[application=%s]", "AFFINITY",
            app1.getApplicationId().toString()));

    am2.allocate(Arrays.asList(rr), null);

    // Do node heartbeats 2 times for each node
    for (int i = 0; i < 2; i++) {
      for (MockNM nm : nms) {
        RMNode rmNode = rm.getRMContext().getRMNodes().get(nm.getNodeId());
        cs.handle(new NodeUpdateSchedulerEvent(rmNode));
      }
    }

    FiCaSchedulerApp schedulerApp2 = cs.getApplicationAttempt(
        am2.getApplicationAttemptId());
    Assert.assertEquals(101, schedulerApp2.getLiveContainers().size());

    // Check affinity allocation, if a node has container from app2, the node
    // should also have container from app1
    for (MockNM nm : nms) {
      SchedulerNode sn = cs.getNode(nm.getNodeId());

      boolean hasApp2Container = false;
      for (RMContainer c : sn.getCopiedListOfRunningContainers()) {
        if (!c.isAMContainer() && c.getApplicationAttemptId().equals(
            am2.getApplicationAttemptId())) {
          hasApp2Container = true;
          break;
        }
      }

      boolean hasApp1Container = false;
      if (hasApp2Container) {
        for (RMContainer c : sn.getCopiedListOfRunningContainers()) {
          if (c.getApplicationAttemptId().equals(am1.getApplicationAttemptId())) {
            hasApp1Container = true;
            break;
          }
        }

        Assert.assertTrue(hasApp1Container);
      }
    }

    rm.close();
  }


  @Test(timeout = 300000)
  public void testAntiAffinityAllocationBetweenApps() throws Exception {
    final RMNodeLabelsManager mgr = new NullRMNodeLabelsManager();
    mgr.init(conf);

    MockRM rm = new MockRM(conf);
    rm.start();
    List<MockNM> nms = initNMs(100, rm);

    // launch an app to queue a1 (label = x), and check all container will
    // be allocated in h1
    RMApp app1 = rm.submitApp(200, "app", "user", null, "default");
    MockAM am1 = MockRM.launchAndRegisterAM(app1, rm, nms.get(0));

    am1.allocate("*", 1024, 80, new ArrayList<>(), "");
    CapacityScheduler cs = (CapacityScheduler) rm.getResourceScheduler();

    // Do node heartbeats 2 times for each node
    for (int i = 0; i < 2; i++) {
      for (MockNM nm : nms) {
        RMNode rmNode = rm.getRMContext().getRMNodes().get(nm.getNodeId());
        cs.handle(new NodeUpdateSchedulerEvent(rmNode));
      }
    }

    // We should have 81 containers allocated for am1
    FiCaSchedulerApp schedulerApp1 = cs.getApplicationAttempt(
        am1.getApplicationAttemptId());
    Assert.assertEquals(81, schedulerApp1.getLiveContainers().size());

    // Now we submit an app2
    RMApp app2 = rm.submitApp(200, "app", "user", null, "default");
    MockAM am2 = MockRM.launchAndRegisterAM(app2, rm, nms.get(0));

    ResourceRequest rr = ResourceRequest.newInstance(Priority.newInstance(0), "*",
        Resources.createResource(1024), 50);
    rr.setPlacementStrategy(String
        .format("op=%s,targets=[application=%s]", "ANTI_AFFINITY",
            app1.getApplicationId().toString()));

    am2.allocate(Arrays.asList(rr), null);

    // Do node heartbeats 10 times for each node
    for (int i = 0; i < 10; i++) {
      for (MockNM nm : nms) {
        RMNode rmNode = rm.getRMContext().getRMNodes().get(nm.getNodeId());
        cs.handle(new NodeUpdateSchedulerEvent(rmNode));
      }
    }

    FiCaSchedulerApp schedulerApp2 = cs.getApplicationAttempt(
        am2.getApplicationAttemptId());
    Assert.assertEquals(51, schedulerApp2.getLiveContainers().size());

    // Check anti affinity allocation, if a node has container from app2, the node
    // should not have container from app1
    for (MockNM nm : nms) {
      SchedulerNode sn = cs.getNode(nm.getNodeId());

      boolean hasApp2Container = false;
      for (RMContainer c : sn.getCopiedListOfRunningContainers()) {
        if (!c.isAMContainer() && c.getApplicationAttemptId().equals(
            am2.getApplicationAttemptId())) {
          hasApp2Container = true;
          break;
        }
      }

      boolean hasApp1Container = false;
      if (hasApp2Container) {
        for (RMContainer c : sn.getCopiedListOfRunningContainers()) {
          if (c.getApplicationAttemptId().equals(am1.getApplicationAttemptId())) {
            hasApp1Container = true;
            break;
          }
        }

        Assert.assertFalse(hasApp1Container);
      }
    }

    rm.close();
  }
}
