package org.apache.hadoop.yarn.server.nodemanager.containermanager.linux.resources;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Allocate GPU resources according to requirements
 */
public class GpuResourceAllocator {
  private Set<Integer> allowedGpuDevices = new TreeSet<>();
  private Map<Integer, String> usedDevices = new TreeMap<>();

  /**
   * Contains allowed and denied devices with minor number.
   * Denied devices will be useful for cgroups devices module to do blacklisting
   */
  static class GpuAllocation {
    private Set<Integer> allowed = Collections.emptySet();
    private Set<Integer> denied = Collections.emptySet();

    GpuAllocation(Set<Integer> allowed, Set<Integer> denied) {
      if (allowed != null) {
        this.allowed = ImmutableSet.copyOf(allowed);
      }
      if (denied != null) {
        this.denied = ImmutableSet.copyOf(denied);
      }
    }

    public Set<Integer> getAllowed() {
      return allowed;
    }

    public Set<Integer> getDenied() {
      return denied;
    }
  }

  /**
   * Add GPU to allowed list
   * @param minorNumber minor number of the GPU device.
   */
  public synchronized void addGpu(int minorNumber) {
    allowedGpuDevices.add(minorNumber);
  }

  private String getResourceHandlerExceptionMessage(int numRequestedGpuDevices,
      String requestorId) {
    return "Failed to find enough GPUs, requestor=" + requestorId
        + ", #RequestedGPUs=" + numRequestedGpuDevices + ", #availableGpus="
        + getAvailableGpus();
  }

  @VisibleForTesting
  public synchronized int getAvailableGpus() {
    return allowedGpuDevices.size() - usedDevices.size();
  }

  /**
   * Assign GPU to requestor
   * @param numRequestedGpuDevices How many GPU to request
   * @param requestorId Id of requestor, such as containerId
   * @return List of denied Gpus with minor numbers
   * @throws ResourceHandlerException When failed to
   */
  public synchronized GpuAllocation assignGpus(int numRequestedGpuDevices,
      String requestorId) throws ResourceHandlerException {
    // Assign Gpus to container if requested some.
    if (numRequestedGpuDevices > 0) {
      if (numRequestedGpuDevices > getAvailableGpus()) {
        throw new ResourceHandlerException(
            getResourceHandlerExceptionMessage(numRequestedGpuDevices,
                requestorId));
      }

      Set<Integer> assignedGpus = new HashSet<>();

      for (int deviceNum : allowedGpuDevices) {
        if (!usedDevices.containsKey(deviceNum)) {
          usedDevices.put(deviceNum, requestorId);
          assignedGpus.add(deviceNum);
          if (assignedGpus.size() == numRequestedGpuDevices) {
            break;
          }
        }
      }

      return new GpuAllocation(assignedGpus,
          Sets.difference(allowedGpuDevices, assignedGpus));
    }
    return new GpuAllocation(null, allowedGpuDevices);
  }

  /**
   * Clean up all Gpus assigned to requestor
   * @param requstorId Id of requestor
   */
  public synchronized void cleanupAssignGpus(String requstorId) {
    Iterator<Map.Entry<Integer, String>> iter =
        usedDevices.entrySet().iterator();
    while (iter.hasNext()) {
      if (iter.next().getValue().equals(requstorId)) {
        iter.remove();
      }
    }
  }
}
