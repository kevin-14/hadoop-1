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

import org.apache.hadoop.yarn.api.records.Priority;
import org.apache.hadoop.yarn.api.records.Resource;
import org.apache.hadoop.yarn.server.resourcemanager.scheduler.SchedulerApplicationAttempt;

public class ResourceRequirement {
  final SchedulerApplicationAttempt application;
  final Resource required;
  final Priority priority;
  final String resourceName;

  public ResourceRequirement(SchedulerApplicationAttempt application,
      Resource required, Priority priority, String resourceName) {
    this.application = application;
    this.required = required;
    this.priority = priority;
    this.resourceName = resourceName;
  }

  public SchedulerApplicationAttempt getApplication() {
    return application;
  }

  public Resource getRequired() {
    return required;
  }
  
  public Priority getPriority() {
    return priority;
  }
  
  public String getResourceName() {
    return resourceName;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof ResourceRequirement)) {
      return false;
    }

    ResourceRequirement other = (ResourceRequirement)obj;

    return application == other.application && required.equals(other.required)
        && priority.equals(other.priority) && resourceName
        .equals(other.resourceName);
  }
}
