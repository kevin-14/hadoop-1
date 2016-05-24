package org.apache.hadoop.yarn.server.resourcemanager.scheduler.common.scorer;

import org.apache.commons.collections.iterators.SingletonIterator;
import org.apache.hadoop.yarn.server.resourcemanager.scheduler.SchedulerNode;
import org.apache.hadoop.yarn.server.resourcemanager.scheduler.common.NodeCandidates;

import java.util.Iterator;

public class DoNotCareNodesScorer<N extends SchedulerNode>
    implements SchedulerNodesScorer<N> {
  @Override
  public Iterator<N> scoreNodeCandidates(
      NodeCandidates<N> candidates) {
    return new SingletonIterator(candidates.getNextAvailable());
  }
}
