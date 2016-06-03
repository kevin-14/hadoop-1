package org.apache.hadoop.yarn.server.resourcemanager.scheduler.common.scorer;

import org.apache.commons.collections.map.LRUMap;
import org.apache.hadoop.yarn.api.records.Priority;
import org.apache.hadoop.yarn.api.records.ResourceRequest;
import org.apache.hadoop.yarn.server.resourcemanager.scheduler.SchedulerApplicationAttempt;
import org.apache.hadoop.yarn.server.resourcemanager.scheduler.SchedulerNode;
import org.apache.hadoop.yarn.server.resourcemanager.scheduler.common.RMPlacementStrategy;

import java.util.Map;

/**
 * Do necessary caching for scorer according to type and applications
 */
public class SchedulerNodesScorerCache {
  // At most store 10K objects
  private static LRUMap lruCache = new LRUMap(1024 * 10);

  private static SchedulerNodesScorerType getSchedulerNodesScorerType(
      SchedulerApplicationAttempt attempt, Priority priority) {
    Map<String, ResourceRequest> requests = attempt.getResourceRequests(
        priority);

    // Check placement strategy
    RMPlacementStrategy ps =
        attempt.getAppSchedulingInfo().getPlacementStrategy(priority);

    //DEBUG
    System.out.println("Placement Strategy:" + ps.getOp());

    if (RMPlacementStrategy.Operator.NO != ps.getOp()) {
      return SchedulerNodesScorerType.AFFINITY_OR_ANTIAFFNITY;
    }

    // Simplest rule to determine with nodes scorer will be used:
    // When requested #resourceName > 0, use locality, otherwise use DO_NOT_CARE
    if (requests != null && requests.size() > 1) {
      return SchedulerNodesScorerType.LOCALITY;
    }

    return SchedulerNodesScorerType.DO_NOT_CARE;
  }

  public static <N extends SchedulerNode> SchedulerNodesScorer<N> getOrCreateScorer(
      SchedulerApplicationAttempt attempt, Priority priority) {
    SchedulerNodesScorerType type = getSchedulerNodesScorerType(attempt,
        priority);

    return getOrCreateScorer(attempt, priority, type);
  }

  public static <N extends SchedulerNode> SchedulerNodesScorer<N> getOrCreateScorer(
      SchedulerApplicationAttempt attempt, Priority priority,
      SchedulerNodesScorerType type) {
    String key =
        attempt.getApplicationAttemptId().toString() + priority.toString();
    SchedulerNodesScorer<N> scorer;
    // scorer = (SchedulerNodesScorer<N>) lruCache.get(key);
    // FIXME: need to correctly compare if we need to update
    scorer = null;

    if (null == scorer) {
      // FIXME, for simple, create scorer every time. We can cache scorer
      // without any issue
      switch (type) {
      case LOCALITY:
        scorer = new LocalityNodesScorer<>(attempt, priority);
        break;
      case DO_NOT_CARE:
        scorer = new DoNotCareNodesScorer<>();
        break;
      case AFFINITY_OR_ANTIAFFNITY:
        scorer = new AffinityOrAntiAffinityScorer(attempt, priority);
        break;
      default:
        return null;
      }

      lruCache.put(key, scorer);
    }

    return scorer;
  }
}
