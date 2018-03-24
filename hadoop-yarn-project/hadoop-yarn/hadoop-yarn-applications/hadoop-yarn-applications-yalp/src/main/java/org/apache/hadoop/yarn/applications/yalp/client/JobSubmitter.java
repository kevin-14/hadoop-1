package org.apache.hadoop.yarn.applications.yalp.client;

import org.apache.hadoop.util.StringUtils;
import org.apache.hadoop.yarn.applications.yalp.Constants;
import org.apache.hadoop.yarn.applications.yalp.client.cli.ClientContext;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.apache.hadoop.yarn.service.api.records.Artifact;
import org.apache.hadoop.yarn.service.api.records.Component;
import org.apache.hadoop.yarn.service.api.records.Resource;
import org.apache.hadoop.yarn.service.api.records.ResourceInformation;
import org.apache.hadoop.yarn.service.api.records.Service;
import org.apache.hadoop.yarn.service.client.ServiceClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Submit a job to cluster
 */
public class JobSubmitter {
  ClientContext clientContext;

  public JobSubmitter(ClientContext clientContext) {
    this.clientContext = clientContext;
  }

  private Resource getServiceResourceFromYarnResource(
      org.apache.hadoop.yarn.api.records.Resource yarnResource) {
    Resource serviceResource = new Resource();
    serviceResource.setCpus(yarnResource.getVirtualCores());
    serviceResource.setMemory(String.valueOf(yarnResource.getMemorySize()));

    Map<String, ResourceInformation> riMap = new HashMap<>();
    for (org.apache.hadoop.yarn.api.records.ResourceInformation ri : yarnResource
        .getAllResourcesListCopy()) {
      ResourceInformation serviceRi =
          new ResourceInformation();
      serviceRi.setValue(ri.getValue());
      serviceRi.setUnit(ri.getUnits());
      riMap.put(ri.getName(), serviceRi);
    }
    serviceResource.setResourceInformations(riMap);

    return serviceResource;
  }

  private Service createServiceByParameters(RunJobParameters parameters) {
    Service service = new Service();
    service.setName(parameters.getName());
    service.setArtifact(new Artifact().type(Artifact.TypeEnum.DOCKER)
        .id(parameters.getDockerImageName()));

    Component workerComponent = new Component();
    workerComponent.setName(Constants.WORKER_COMPONENT_NAME);
    workerComponent.setNumberOfContainers((long) parameters.getNumWorkers());
    workerComponent.setResource(
        getServiceResourceFromYarnResource(parameters.getWorkerResource()));
    workerComponent.setLaunchCommand(
        StringUtils.join(" ", parameters.getWorkerLaunchCmd()));

    Component psComponent = new Component();
    psComponent.setName(Constants.PS_COMPONENT_NAME);
    psComponent.setNumberOfContainers((long) parameters.getNumPS());
    psComponent.setResource(
        getServiceResourceFromYarnResource(parameters.getPsResource()));

    // TODO
    psComponent.setLaunchCommand(
        StringUtils.join(" ", parameters.getWorkerLaunchCmd()));

    // Add all components
    List<Component> componentList = new ArrayList<>();
    componentList.add(workerComponent);
    componentList.add(psComponent);

    return service;
  }

  /**
   * Run a job by given parameters, returns when job sucessfully submitted to
   * RM.
   * @param parameters parameters
   * @throws IOException
   * @throws YarnException
   */
  public void runJob(RunJobParameters parameters)
      throws IOException, YarnException {
    Service service = createServiceByParameters(parameters);
    ServiceClient serviceClient = new ServiceClient();
    serviceClient.init(clientContext.getConfiguration());
    serviceClient.start();
    serviceClient.actionCreate(service);
  }
}
