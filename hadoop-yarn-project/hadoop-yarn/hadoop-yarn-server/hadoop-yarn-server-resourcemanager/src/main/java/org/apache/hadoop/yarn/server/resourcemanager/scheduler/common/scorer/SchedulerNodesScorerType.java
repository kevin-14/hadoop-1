package org.apache.hadoop.yarn.server.resourcemanager.scheduler.common.scorer;

public enum SchedulerNodesScorerType {
  DO_NOT_CARE, // Any node is fine
  LOCALITY, // Locality-based
}
