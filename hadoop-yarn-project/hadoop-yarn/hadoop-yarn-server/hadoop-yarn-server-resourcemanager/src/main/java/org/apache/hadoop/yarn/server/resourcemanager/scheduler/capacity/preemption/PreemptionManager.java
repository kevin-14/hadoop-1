/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.yarn.server.resourcemanager.scheduler.capacity.preemption;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.hadoop.yarn.api.records.ApplicationAttemptId;
import org.apache.hadoop.yarn.api.records.ContainerId;
import org.apache.hadoop.yarn.api.records.Resource;
import org.apache.hadoop.yarn.server.resourcemanager.rmcontainer.RMContainer;
import org.apache.hadoop.yarn.server.resourcemanager.scheduler.SchedulerApplicationAttempt;
import org.apache.hadoop.yarn.server.resourcemanager.scheduler.capacity.CapacitySchedulerContext;
import org.apache.hadoop.yarn.util.resource.ResourceCalculator;
import org.apache.hadoop.yarn.util.resource.Resources;

import com.google.common.collect.Lists;

public class PreemptionManager {
  enum PreemptionType {
    // Currently only DIFFERENCE_QUEUE will be used.
    DIFFERENT_QUEUE, 
    SAME_QUEUE_DIFFERENT_USER, 
    SAME_QUEUE_SAME_USER
  }

  static class ToPreemptContainer {
    RMContainer container;
    SchedulerApplicationAttempt application;
    long startTimestamp;
    PreemptionType preemptionType;

    public ToPreemptContainer(RMContainer container,
        SchedulerApplicationAttempt application, long startTimestamp,
        PreemptionType preemptionType) {
      this.container = container;
      this.application = application;
      this.startTimestamp = startTimestamp;
      this.preemptionType = preemptionType;
    }
    
    /*
     * Entity key could be used to index which {@link PreemptableEntityMeasure}
     */
    public String getEntityKey() {
      return container.getQueue() + "_" + container.getNodeParititon();
    }
  }
  
  ResourceCalculator rc;

  /*
   * One preemptable entity, this could be a queue, a user or application.
   * User/Application will be used only when intra-queue preemption supported
   */
  static class PreemptableEntityMeasure {
    String entityKey;

    // PlaceHolder: String parentKey;
    // PlaceHolder: String user;
    // PlaceHolder: String appId;

    Resource ideal;
    Resource maxPreemptable;
    Resource totalMarkedPreempted;
    
    private Resource totalMarkedPreemptedForDryRun;
    private long timestamp;

    public PreemptableEntityMeasure(String entityKey) {
      this.entityKey = entityKey;
      this.totalMarkedPreempted = Resources.createResource(0);
    }
    
    public Resource getTotalMarkedPreemptedForDryRun(long timestamp) {
      if (this.timestamp != timestamp) {
        totalMarkedPreemptedForDryRun = Resources.clone(totalMarkedPreempted);
        this.timestamp = timestamp;
      }
      return totalMarkedPreemptedForDryRun;
    }
  }

  class PreemptableEntitiesManager {
    private Map<String, PreemptableEntityMeasure> map = new HashMap<>();
    
    private PreemptableEntityMeasure getOrAddNew(String key) {
      PreemptableEntityMeasure measure = map.get(key);
      if (measure == null) {
        measure = new PreemptableEntityMeasure(key);
        map.put(key, measure);
      }
      return measure;
    }

    public void updatePreemptableQueueEntity(String queue, String partition,
        Resource ideal, Resource maxPreempt) {
      String key = queue + "_" + partition;

      PreemptableEntityMeasure measure = getOrAddNew(key);
      measure.ideal = ideal;
      measure.maxPreemptable = maxPreempt;
    }
  }

  static class PreemptionRelationshipManager {
    Map<ContainerId, ToPreemptContainer> toPreemptContainers =
        new HashMap<>();
    Map<ApplicationAttemptId, Set<ContainerId>> applicationToPreemptCandidates =
        new HashMap<>();
  }
  
  private List<RMContainer> getContainersToPreempt(List<RMContainer> candidates,
      Resource required, CapacitySchedulerContext csContext) {
    Resource cluster = csContext.getClusterResource();
    Resource minimumAllocation = csContext.getMinimumResourceCapability();
    
    // This approach assumes value of minimum-resource is reasonqble
    // TODO, use different approach if nodeResource / minimumAllocation is too
    // large 
    
    // Use solution of knapsack problem to get best combination of preempted
    // containers. 
    int normalizedTotal = Math.round(Resources.divide(rc,
        csContext.getClusterResource(), required, minimumAllocation));
    
    List<List<RMContainer>> f = new ArrayList<List<RMContainer>>(normalizedTotal + 1);
    
    for (int i = 0; i < normalizedTotal + 1; i++) {
      f.add(Collections.<RMContainer> emptyList());
    }
    
    for (int i = 0; )
  }

  public boolean tryToPreempt(ResourceRequirement resourceRequirement,
      Collection<RMContainer> candidatesToPreempt,
      CapacitySchedulerContext csContext) {
    for (PreemptionType preemptionType : Arrays.asList(
        PreemptionType.DIFFERENT_QUEUE,
        PreemptionType.SAME_QUEUE_DIFFERENT_USER,
        PreemptionType.SAME_QUEUE_SAME_USER)) {
      List<RMContainer> candidates = getContainers(preemptionType,
          resourceRequirement, candidatesToPreempt);

    }
  }

  PreemptionType getPreemptionType(ResourceRequirement requirement,
      RMContainer preemptionCandidate) {
    if (!requirement.getApplication().getQueue()
        .equals(preemptionCandidate.getQueue())) {
      return PreemptionType.DIFFERENT_QUEUE;
    } else if (!requirement.getApplication().getUser()
        .equals(preemptionCandidate.getUser())) {
      return PreemptionType.SAME_QUEUE_DIFFERENT_USER;
    } else {
      return PreemptionType.SAME_QUEUE_SAME_USER;
    }
  }

  List<RMContainer> getContainers(PreemptionType preemptionType,
      ResourceRequirement resourceRequirement,
      Collection<RMContainer> candidates) {
    List<RMContainer> containers = new ArrayList<>();
    for (RMContainer container : candidates) {
      if (getPreemptionType(resourceRequirement, container) == preemptionType) {
        containers.add(container);
      }
    }

    return containers;
  }
}
