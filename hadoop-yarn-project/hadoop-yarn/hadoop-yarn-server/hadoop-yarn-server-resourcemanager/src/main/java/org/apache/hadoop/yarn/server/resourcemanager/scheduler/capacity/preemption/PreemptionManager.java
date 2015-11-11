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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.hadoop.yarn.api.records.ApplicationAttemptId;
import org.apache.hadoop.yarn.api.records.ContainerId;
import org.apache.hadoop.yarn.api.records.Priority;
import org.apache.hadoop.yarn.api.records.Resource;
import org.apache.hadoop.yarn.server.resourcemanager.rmcontainer.RMContainer;
import org.apache.hadoop.yarn.server.resourcemanager.scheduler.SchedulerApplicationAttempt;
import org.apache.hadoop.yarn.server.resourcemanager.scheduler.capacity.CapacitySchedulerContext;
import org.apache.hadoop.yarn.util.resource.ResourceCalculator;
import org.apache.hadoop.yarn.util.resource.Resources;

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
    
    private PreemptableEntityMeasure get(String key) {
      return map.get(key);
    }

    public void updatePreemptableQueueEntity(String queue, String partition,
        Resource ideal, Resource maxPreempt) {
      String key = queue + "_" + partition;

      PreemptableEntityMeasure measure = getOrAddNew(key);
      measure.ideal = ideal;
      measure.maxPreemptable = maxPreempt;
    }
  }
  
  static class DemandingApp {
    ApplicationAttemptId appAttemptId;
    SchedulerApplicationAttempt application;
    // to-preempt containers for this app only
    Map<ContainerId, ToPreemptContainer> toPreemptContainers;
    // to-preempt resources, priority -> <resource-name,
    //    how-much-resource-marked-to-be-preempted-from-other-applications>
    Map<Priority, Map<String, Resource>> toPreemptResources;
    // container to reference of how much resource marked to be preemption classified by priority and resourceName (the reference of resource in above map)
    Map<ContainerId, Resource> containerIdToToPreemptResource;
    
    public DemandingApp(ApplicationAttemptId appAttemptId,
        SchedulerApplicationAttempt application) {
      this.appAttemptId = appAttemptId;
      this.application = application;
      
      toPreemptContainers = new HashMap<>();
      toPreemptResources = new HashMap<>();
    }
    
    void addToPreemptContainer(ToPreemptContainer container, Priority priority,
        String resourceName) {
      ContainerId containerId = container.container.getContainerId();
      
      toPreemptContainers.put(containerId, container);
      if (!toPreemptResources.containsKey(priority)) {
        toPreemptResources.put(priority, new HashMap<String, Resource>());
      }
      if (!toPreemptResources.get(priority).containsKey(resourceName)) {
        toPreemptResources.get(priority).put(resourceName,
            Resources.createResource(0));
      }
      
      Resource resource = toPreemptResources.get(priority).get(resourceName);
      containerIdToToPreemptResource.put(containerId, resource);
      
      Resources.addTo(resource,
          container.container.getAllocatedResource());
    }
    
    void containerCompleted(ContainerId completedContainer) {
      if (toPreemptContainers.containsKey(completedContainer)) {
        ToPreemptContainer container = toPreemptContainers.remove(completedContainer);
        Resources.subtractFrom(
            containerIdToToPreemptResource
                .get(container.container.getContainerId()),
            container.container.getAllocatedResource());
      }
    }
  }

  class PreemptionRelationshipManager {
    Map<ContainerId, ToPreemptContainer> toPreemptContainers =
        new HashMap<>();
    Map<ApplicationAttemptId, DemandingApp> demandingApps =
        new HashMap<>();
    
    void addToPreemptContainer(ToPreemptContainer container,
        SchedulerApplicationAttempt application, Priority priority,
        String resourceName) {
      ApplicationAttemptId attemptId = application.getApplicationAttemptId();
      if (!demandingApps.containsKey(attemptId)) {
        demandingApps.put(attemptId, new DemandingApp(attemptId, application));
      }
      toPreemptContainers.put(container.container.getContainerId(), container);
      demandingApps.get(attemptId).addToPreemptContainer(container, priority,
          resourceName);
    }
    
    void containerCompleted(ContainerId completedContainer) {
      if (toPreemptContainers.containsKey(completedContainer)) {
        ToPreemptContainer container =
            toPreemptContainers.remove(completedContainer);
        DemandingApp app =
            demandingApps.get(container.application.getApplicationAttemptId());
        if (app != null) {
          app.containerCompleted(completedContainer);
        }
      }
    }
  }
  
  ResourceCalculator rc;
  PreemptableEntitiesManager preemptableEntitiesManager =
      new PreemptableEntitiesManager();
  PreemptionRelationshipManager preemptionReleationshipManager =
      new PreemptionRelationshipManager();
  
  private List<RMContainer> selectContainersToPreempt(List<RMContainer> candidates,
      Resource required, Resource cluster) {
    long timestamp = System.nanoTime();
    
    // Assume candidates is sorted by preemption order, first items will be preempted first.
    // Scan the list to select which containers to be preempted.
    Resource totalSelected = Resources.createResource(0);
    List<RMContainer> selected = new ArrayList<>();
    
    for (RMContainer candidateContainer : candidates) {
      String key = candidateContainer.getQueue() + "_"
          + candidateContainer.getNodeParititon();
      PreemptableEntityMeasure measure = preemptableEntitiesManager.get(key);
      if (measure == null) {
        continue;
      }
      
      Resource markedPreempted =
          measure.getTotalMarkedPreemptedForDryRun(timestamp);
      Resource markedPreemptedIfCandidateSelected = Resources
          .add(markedPreempted, candidateContainer.getAllocatedResource());
      
      // We get enough preemption headroom for the candidate
      if (Resources.lessThanOrEqual(rc, cluster,
          markedPreemptedIfCandidateSelected,
          candidateContainer.getAllocatedResource())) {
        Resources.addTo(markedPreempted,
            candidateContainer.getAllocatedResource());
        selected.add(candidateContainer);
      }
      
      // update total resource as well
      Resources.addTo(totalSelected, candidateContainer.getAllocatedResource());
      if (Resources.fitsIn(required, totalSelected)) {
        return selected;
      }
    }
    
    return null;
  }

  public boolean tryToPreempt(ResourceRequirement resourceRequirement,
      Collection<RMContainer> candidatesToPreempt,
      CapacitySchedulerContext csContext) {
    List<RMContainer> candidates = getContainers(PreemptionType.DIFFERENT_QUEUE,
        resourceRequirement, candidatesToPreempt);
    
    List<RMContainer> selectedContainers = selectContainersToPreempt(candidates,
        resourceRequirement.getRequired(), csContext.getClusterResource());
    
    if (selectedContainers != null) {
      // Updates container preemption info
      for (RMContainer c : selectedContainers) {
        String key = c.getQueue() + "_" + c.getNodeParititon();
        PreemptableEntityMeasure measure = preemptableEntitiesManager.get(key);
        measure.totalMarkedPreempted = measure.totalMarkedPreemptedForDryRun;
      }
      
      return true;
    }
    
    return false;
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
