package org.apache.hadoop.yarn.submarine.client.common;

import org.apache.hadoop.yarn.service.api.records.Component;
import org.apache.hadoop.yarn.service.api.records.Container;
import org.apache.hadoop.yarn.service.api.records.ContainerState;
import org.apache.hadoop.yarn.service.api.records.Service;
import org.apache.hadoop.yarn.service.api.records.ServiceState;
import org.apache.hadoop.yarn.service.utils.JsonSerDeser;
import org.apache.hadoop.yarn.submarine.client.common.param.JobRunParameters;
import org.codehaus.jackson.map.PropertyNamingStrategy;

import java.io.IOException;
import java.io.PrintStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.apache.hadoop.yarn.service.utils.ServiceApiUtil.jsonSerDeser;
import static org.apache.hadoop.yarn.submarine.client.common.Constants.PRIMARY_WORKER_COMPONENT_NAME;
import static org.apache.hadoop.yarn.submarine.client.common.Constants.WORKER_COMPONENT_NAME;

public class TrainingJobStatus {
  public static JsonSerDeser<Service> jsonSerDeser =
      new JsonSerDeser<>(Service.class,
          PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES);

  public static class ComponentStatus {
    long nReadyContainers = 0;
    long nRunningButUnreadyContainers = 0;
    long totalAskedContainers;
    String compName;

    public ComponentStatus(Component component) {
      totalAskedContainers = component.getNumberOfContainers();
      compName = component.getName();
      for (Container c : component.getContainers()) {
        if (c.getState() == ContainerState.READY) {
          nReadyContainers++;
        } else if (c.getState() == ContainerState.RUNNING_BUT_UNREADY) {
          nRunningButUnreadyContainers++;
        }
      }
    }
  }

  private ServiceState state;
  private String tensorboardLink = "N/A";
  private List<ComponentStatus> componentStatus;
  private Service serviceSpec;

  public void nicePrint(PrintStream out) {
    out.println("Job Name=" + serviceSpec.getName() + ", status=" + state.name()
        + " time=" + Instant.now());
    if (state == ServiceState.FAILED || state == ServiceState.STOPPED) {
      return;
    }

    if (tensorboardLink.startsWith("http")) {
      out.println("  Tensorboard link: " + " http://172.27.71.0:6006/");
    }

    out.println("  Components:");
    for (ComponentStatus comp : componentStatus) {
      out.println("    [" + comp.compName + "] Ready=" + comp.nReadyContainers
          + " + Running-But-Non-Ready=" + comp.nRunningButUnreadyContainers
          + " | Asked=" + comp.totalAskedContainers);
    }
    out.println("------------------");
  }

  public void printFullJson(PrintStream out) throws IOException {
    if (serviceSpec != null) {
      out.println(jsonSerDeser.toJson(serviceSpec));
    }
  }

  private void fetchTensorboardLink(ClientContext clientContext) {
    JobRunParameters jobRunParameters = clientContext.getRunJobParameters(
        serviceSpec.getName());
    if (jobRunParameters == null || !jobRunParameters.isTensorboardEnabled()) {
      return;
    }

    // If it is a final state, return.
    if (state == ServiceState.STOPPED
        || state == ServiceState.FAILED) {
      return;
    }

    for (Component component : serviceSpec.getComponents()) {
      if (component.getName().equals(PRIMARY_WORKER_COMPONENT_NAME)) {
        for (Container c : component.getContainers()) {
          if (c.getComponentInstanceName().equals(PRIMARY_WORKER_COMPONENT_NAME + "-0")
              && (c.getState() == ContainerState.READY
              || c.getState() == ContainerState.RUNNING_BUT_UNREADY)) {
            String hostname = c.getHostname();
            int port = clientContext.getTaskNetworkPortManager().getPort(
                serviceSpec.getName(), "tensorboard", 0);
            tensorboardLink = "http://" + hostname + ":" + port;
          }
        }
      }
    }
  }

  public static TrainingJobStatus fromServiceSepc(Service serviceSpec,
      ClientContext clientContext) {
    TrainingJobStatus status = new TrainingJobStatus();
    status.state = serviceSpec.getState();
    status.serviceSpec = serviceSpec;

    // If it is a final state, return.
    if (status.state == ServiceState.STOPPED
        || status.state == ServiceState.FAILED) {
      return status;
    }

    status.componentStatus = new ArrayList<>();

    for (Component component : serviceSpec.getComponents()) {
      status.componentStatus.add(new ComponentStatus(component));
    }

    status.fetchTensorboardLink(clientContext);

    return status;
  }


  public ServiceState getState() {
    return state;
  }

  public String getTensorboardLink() {
    return tensorboardLink;
  }

  public List<ComponentStatus> getComponentStatus() {
    return componentStatus;
  }

  public Service getServiceSpec() {
    return serviceSpec;
  }
}
