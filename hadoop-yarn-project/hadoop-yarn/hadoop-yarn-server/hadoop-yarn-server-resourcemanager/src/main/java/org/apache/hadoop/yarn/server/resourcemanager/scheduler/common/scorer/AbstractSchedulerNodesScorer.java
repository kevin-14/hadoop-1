package org.apache.hadoop.yarn.server.resourcemanager.scheduler.common.scorer;

import org.apache.hadoop.yarn.api.records.Priority;
import org.apache.hadoop.yarn.server.resourcemanager.scheduler.SchedulerApplicationAttempt;
import org.apache.hadoop.yarn.server.resourcemanager.scheduler.SchedulerNode;

import java.util.concurrent.locks.ReentrantReadWriteLock;

public abstract class AbstractSchedulerNodesScorer<N extends SchedulerNode>
    implements SchedulerNodesScorer<N> {
  SchedulerApplicationAttempt attempt;
  Priority priority;
  ReentrantReadWriteLock.ReadLock readLock;
  ReentrantReadWriteLock.WriteLock writeLock;

  AbstractSchedulerNodesScorer(SchedulerApplicationAttempt attempt,
      Priority priority) {
    this.attempt = attempt;
    this.priority = priority;
    ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    readLock = lock.readLock();
    writeLock = lock.writeLock();
  }
}
