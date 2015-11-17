package org.apache.hadoop.yarn.server.resourcemanager.scheduler.capacity.preemption;

import java.util.ArrayList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.service.Service;
import org.apache.hadoop.yarn.api.records.ContainerId;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.server.resourcemanager.MockAM;
import org.apache.hadoop.yarn.server.resourcemanager.MockNM;
import org.apache.hadoop.yarn.server.resourcemanager.MockRM;
import org.apache.hadoop.yarn.server.resourcemanager.ResourceManager.RMActiveServices;
import org.apache.hadoop.yarn.server.resourcemanager.monitor.SchedulingEditPolicy;
import org.apache.hadoop.yarn.server.resourcemanager.monitor.SchedulingMonitor;
import org.apache.hadoop.yarn.server.resourcemanager.monitor.capacity.ProportionalCapacityMonitorPolicy;
import org.apache.hadoop.yarn.server.resourcemanager.nodelabels.NullRMNodeLabelsManager;
import org.apache.hadoop.yarn.server.resourcemanager.nodelabels.RMNodeLabelsManager;
import org.apache.hadoop.yarn.server.resourcemanager.rmapp.RMApp;
import org.apache.hadoop.yarn.server.resourcemanager.rmnode.RMNode;
import org.apache.hadoop.yarn.server.resourcemanager.scheduler.ResourceScheduler;
import org.apache.hadoop.yarn.server.resourcemanager.scheduler.capacity.CapacityScheduler;
import org.apache.hadoop.yarn.server.resourcemanager.scheduler.capacity.TestUtils;
import org.apache.hadoop.yarn.server.resourcemanager.scheduler.common.fica.FiCaSchedulerApp;
import org.apache.hadoop.yarn.server.resourcemanager.scheduler.event.NodeUpdateSchedulerEvent;
import org.apache.hadoop.yarn.util.Clock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestCapacitySchedulerPreemption {
  private static final Log LOG = LogFactory
      .getLog(TestCapacitySchedulerPreemption.class);

  private final int GB = 1024;

  private YarnConfiguration conf;
  
  RMNodeLabelsManager mgr;

  Clock clock;

  @Before
  public void setUp() throws Exception {
    conf = new YarnConfiguration();
    conf.setClass(YarnConfiguration.RM_SCHEDULER, CapacityScheduler.class,
      ResourceScheduler.class);
    conf.setBoolean(YarnConfiguration.RM_SCHEDULER_ENABLE_MONITORS, true);
    conf.setClass(YarnConfiguration.RM_SCHEDULER_MONITOR_POLICIES,
        ProportionalCapacityMonitorPolicy.class,
        SchedulingEditPolicy.class);
    mgr = new NullRMNodeLabelsManager();
    mgr.init(conf);
    clock = mock(Clock.class);
    when(clock.getTime()).thenReturn(0L);
  }

  private SchedulingEditPolicy getSchedulingEditPolicy(MockRM rm) {
    RMActiveServices activeServices = rm.getRMActiveService();
    SchedulingMonitor mon = null;
    for (Service service : activeServices.getServices()) {
      if (service instanceof SchedulingMonitor) {
        mon = (SchedulingMonitor) service;
        break;
      }
    }
    
    if (mon != null) {
      return mon.getSchedulingEditPolicy();
    }
    return null;
  }
  
  
  @Test //(timeout = 60000)
  public void testSimplePreemptionWithDifferentContainersSize() throws Exception {
    /**
     * Test case: Submit two application (app1/app2) to different queues, queue
     * structure:
     * 
     * <pre>
     *             Root
     *            /  |  \
     *           a   b   c
     *          10   20  70
     * </pre>
     * 
     * Two nodes in the cluster, each of them has 4G.
     * 
     * app1 submit to queue-a first, it asked 8 * 1G containers, so there's no
     * more resource available.
     * 
     * app2 submit to queue-b, ask for one 4G container, it should preempt 4 *
     * 1G containers
     */
    MockRM rm1 = new MockRM(TestUtils.getConfigurationWithMultipleQueues(conf));

    rm1.getRMContext().setNodeLabelManager(mgr);
    rm1.start();
    MockNM nm1 = rm1.registerNode("h1:1234", 4 * GB);
    MockNM nm2 = rm1.registerNode("h2:1234", 4 * GB);
    CapacityScheduler cs = (CapacityScheduler) rm1.getResourceScheduler();
    RMNode rmNode1 = rm1.getRMContext().getRMNodes().get(nm1.getNodeId());
    RMNode rmNode2 = rm1.getRMContext().getRMNodes().get(nm2.getNodeId());

    // launch an app to queue, AM container should be launched in nm1
    RMApp app1 = rm1.submitApp(1 * GB, "app", "user", null, "a");
    MockAM am1 = MockRM.launchAndRegisterAM(app1, rm1, nm1);
  
    am1.allocate("*", 1 * GB, 7, new ArrayList<ContainerId>());

    // Do allocation 4 times for node1/node2
    for (int i = 0; i < 4; i++) {
      cs.handle(new NodeUpdateSchedulerEvent(rmNode1));
      cs.handle(new NodeUpdateSchedulerEvent(rmNode2));
    }
    
    // App1 should have 8 containers now, and no available resource for cluster
    FiCaSchedulerApp schedulerApp1 =
        cs.getApplicationAttempt(am1.getApplicationAttemptId());

    // Check if a 4G container allocated for app1, and nothing allocated for app2
    Assert.assertEquals(8, schedulerApp1.getLiveContainers().size());
    
    // NM1/NM2 has available resource = 0G
    Assert.assertEquals(0 * GB, cs.getNode(nm1.getNodeId())
        .getAvailableResource().getMemory());
    Assert.assertEquals(0 * GB, cs.getNode(nm2.getNodeId())
        .getAvailableResource().getMemory());
    
    // Submit app2 to queue-b and asks for a 4G container for AM
    RMApp app2 = rm1.submitApp(4 * GB, "app", "user", null, "b");

    // Get edit policy and do one update
    SchedulingEditPolicy editPolicy = getSchedulingEditPolicy(rm1);
    editPolicy.editSchedule();
    PreemptionManager pm = cs.getPreemptionManager();
    pm.setClock(clock);
    
    // Do a dryrun node update for nm1
    // It shouldn't update to-preempt container since AM container is on nm1 
    cs.allocateContainersToNode(cs.getNode(nm1.getNodeId()), true);
    Assert.assertEquals(0,
        pm.preemptionReleationshipManager.toPreemptContainers.size());
    
    // Do a dryrun node update for nm2
    // All 4 containers from app1 should be listed on to-preempt containers 
    cs.allocateContainersToNode(cs.getNode(nm2.getNodeId()), true);
    Assert.assertEquals(4,
        pm.preemptionReleationshipManager.toPreemptContainers.size());

    // After wait-before-kill time, do another dryrun for nm2, check if containers added
    // to to-kill list
    when(clock.getTime()).thenReturn(pm.getWaitBeforeKillMs() + 1);
    cs.allocateContainersToNode(cs.getNode(nm2.getNodeId()), true);
    Assert.assertEquals(4, pm.toKillContainers.size());

    cs.allocateContainersToNode(cs.getNode(nm2.getNodeId()), false);
    FiCaSchedulerApp schedulerApp2 =
        cs.getSchedulerApplications().get(app2.getApplicationId())
            .getCurrentAppAttempt();
    Assert.assertEquals(1, schedulerApp2.getLiveContainers().size());
    Assert.assertEquals(4, schedulerApp1.getLiveContainers().size());

    rm1.close();
  }
}
