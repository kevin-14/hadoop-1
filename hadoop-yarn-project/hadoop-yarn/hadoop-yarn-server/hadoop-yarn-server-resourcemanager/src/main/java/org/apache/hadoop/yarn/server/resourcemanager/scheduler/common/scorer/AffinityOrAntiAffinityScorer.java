package org.apache.hadoop.yarn.server.resourcemanager.scheduler.common.scorer;

import org.apache.commons.collections.IteratorUtils;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.Priority;
import org.apache.hadoop.yarn.server.resourcemanager.rmcontainer.RMContainer;
import org.apache.hadoop.yarn.server.resourcemanager.scheduler.SchedulerApplicationAttempt;
import org.apache.hadoop.yarn.server.resourcemanager.scheduler.SchedulerNode;
import org.apache.hadoop.yarn.server.resourcemanager.scheduler.common.NodeCandidates;
import org.apache.hadoop.yarn.server.resourcemanager.scheduler.common.RMPlacementStrategy;
import org.apache.hadoop.yarn.util.ConverterUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class AffinityOrAntiAffinityScorer<N extends SchedulerNode>
    extends AbstractSchedulerNodesScorer<N> {
  AffinityOrAntiAffinityScorer(SchedulerApplicationAttempt attempt,
      Priority priority) {
    super(attempt, priority);
  }

  private boolean canUse(RMPlacementStrategy ps, N node) {
    boolean contains = false;

    ApplicationId targetApplicationId = (ApplicationId) ps.getTargets().get(
        RMPlacementStrategy.TargetType.APPLICATION);
    if (null == targetApplicationId) {
      targetApplicationId = attempt.getApplicationId();
    }
    Priority targetPriority = (Priority) ps.getTargets().get(
        RMPlacementStrategy.TargetType.PRIORITY);

    for (RMContainer c : node.getCopiedListOfRunningContainers()) {
      if (contains) {
        break;
      }

      if (c.getApplicationAttemptId().getApplicationId().equals(
          targetApplicationId)) {
        if (targetPriority != null) {
          if (targetPriority.equals(c.getAllocatedPriority()) || targetPriority
              .equals(c.getReservedPriority())) {
            contains = true;
            break;
          }
        } else {
          contains = true;
          break;
        }
      }
    }

    if (ps.getOp() == RMPlacementStrategy.Operator.ANTI_AFFINITY) {
      return !contains;
    } else if (ps.getOp() == RMPlacementStrategy.Operator.AFFINITY) {
      return contains;
    }

    return false;
  }

  @Override
  public Iterator<N> scoreNodeCandidates(NodeCandidates<N> candidates) {
    RMPlacementStrategy strategy =
        attempt.getAppSchedulingInfo().getPlacementStrategy(priority);
    if (null == strategy
        || strategy.getOp() == RMPlacementStrategy.Operator.NO) {
      return IteratorUtils.singletonIterator(candidates.getNextAvailable());
    }

    List<N> usableNodes = new ArrayList<>();
    for (N n : candidates.getAllSchedulableNodes().values()) {
      if (canUse(strategy, n)) {
        usableNodes.add(n);
      }
    }

    return usableNodes.iterator();
  }
}
