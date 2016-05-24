package org.apache.hadoop.yarn.server.resourcemanager.scheduler.common.scorer;

import org.apache.commons.collections.IteratorUtils;
import org.apache.hadoop.yarn.api.records.NodeId;
import org.apache.hadoop.yarn.api.records.Priority;
import org.apache.hadoop.yarn.api.records.ResourceRequest;
import org.apache.hadoop.yarn.server.resourcemanager.scheduler.SchedulerApplicationAttempt;
import org.apache.hadoop.yarn.server.resourcemanager.scheduler.SchedulerNode;
import org.apache.hadoop.yarn.server.resourcemanager.scheduler.common.NodeCandidates;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class LocalityNodesScorer<N extends SchedulerNode>
    extends AbstractSchedulerNodesScorer<N> {
  private long lastInitializedTime = 0;

  private ConcurrentLinkedQueue<N> nodeLocalHosts;
  private ConcurrentLinkedQueue<N> rackLocalHosts;
  private ConcurrentLinkedQueue<N> offswitchHosts;

  public LocalityNodesScorer(SchedulerApplicationAttempt attempt,
      Priority priority) {
    super(attempt, priority);
  }

  private void reinitializeIfNeeded(NodeCandidates<N> candidates) {
    // Do not reinitialize in 5000 ms.
    // FIXME: this should be configurable and will be forced to update when
    // Requirement changes, etc.
    if (System.currentTimeMillis() - 5000L < lastInitializedTime) {
      return;
    }

    lastInitializedTime = System.currentTimeMillis();

    try {
      writeLock.lock();
      if (null == nodeLocalHosts) {
        nodeLocalHosts = new ConcurrentLinkedQueue<>();
        rackLocalHosts = new ConcurrentLinkedQueue<>();
        offswitchHosts = new ConcurrentLinkedQueue<>();
      } else {
        nodeLocalHosts.clear();
        rackLocalHosts.clear();
        offswitchHosts.clear();
      }

      // We don't need any resource
      boolean needResource = attempt.getResourceRequest(priority,
          ResourceRequest.ANY).getNumContainers() > 0;
      if (!needResource) {
        return;
      }

      for (Map.Entry<NodeId, N> entry : candidates.getAllSchedulableNodes().entrySet()) {
        NodeId nodeId = entry.getKey();
        N node = entry.getValue();
        String rack = node.getRackName();

        ResourceRequest rr = attempt.getAppSchedulingInfo().getResourceRequest(
            priority, nodeId.getHost());
        if (rr != null && rr.getNumContainers() > 0) {
          nodeLocalHosts.add(node);
        } else {
          rr = attempt.getAppSchedulingInfo().getResourceRequest(priority, rack);
          boolean hasRackLocalRequest = rr != null && rr.getNumContainers() > 0;
          if (hasRackLocalRequest) {
            rackLocalHosts.add(node);
          } else {
            offswitchHosts.add(node);
          }
        }
      }
    } finally {
      writeLock.unlock();
    }
  }

  private void moveFirstToLast(ConcurrentLinkedQueue<N> queue) {
    N n = null;
    try {
      n = queue.poll();
    } catch (NoSuchElementException e) {
      // do nothing;
    }

    if (n != null) {
      queue.add(n);
    }
  }

  @Override
  public Iterator<N> scoreNodeCandidates(
      NodeCandidates<N> candidates) {
    reinitializeIfNeeded(candidates);

    try {
      writeLock.lock();
      moveFirstToLast(nodeLocalHosts);
      moveFirstToLast(rackLocalHosts);
      moveFirstToLast(offswitchHosts);
    } finally {
      writeLock.unlock();
    }

    try {
      readLock.lock();
      return IteratorUtils.chainedIterator(
          new Iterator[] { nodeLocalHosts.iterator(), rackLocalHosts.iterator(),
              offswitchHosts.iterator() });
    } finally {
      readLock.unlock();
    }
  }
}
