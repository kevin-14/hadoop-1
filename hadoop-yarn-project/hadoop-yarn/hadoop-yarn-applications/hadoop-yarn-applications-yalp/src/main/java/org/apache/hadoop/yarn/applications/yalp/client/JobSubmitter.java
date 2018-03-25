/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. See accompanying LICENSE file.
 */

package org.apache.hadoop.yarn.applications.yalp.client;

import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.yarn.applications.yalp.client.common.ClientContext;
import org.apache.hadoop.yarn.applications.yalp.client.common.Constants;
import org.apache.hadoop.yarn.applications.yalp.client.common.RunJobParameters;
import org.apache.hadoop.yarn.applications.yalp.client.common.TaskType;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.apache.hadoop.yarn.service.api.ServiceApiConstants;
import org.apache.hadoop.yarn.service.api.records.Artifact;
import org.apache.hadoop.yarn.service.api.records.Component;
import org.apache.hadoop.yarn.service.api.records.ConfigFile;
import org.apache.hadoop.yarn.service.api.records.Resource;
import org.apache.hadoop.yarn.service.api.records.ResourceInformation;
import org.apache.hadoop.yarn.service.api.records.Service;
import org.apache.hadoop.yarn.service.client.ServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.apache.hadoop.yarn.service.api.records.ConfigFile.TypeEnum.YAML;

/**
 * Submit a job to cluster
 */
public class JobSubmitter {
  private static final Logger LOG =
      LoggerFactory.getLogger(JobSubmitter.class);
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

  private void addHdfsClassPathIfNeeded(RunJobParameters parameters,
      FileWriter fw) throws IOException {
    if ((parameters.getInput() != null && parameters.getInput().contains(
        "hdfs://")) || (parameters.getOutput() != null && parameters.getOutput()
        .contains("hdfs://"))) {
      // HDFS is asked either in input or output, set LD_LIBRARY_PATH
      // and classpath
      fw.append("export LD_LIBRARY_PATH=`hadoop jnipath`\n");
      fw.append("export CLASSPATH=`hadoop classpath --glob`\n");
    }
  }

  /*
   * Generate a command launch script on local disk, returns patch to the script
   */
  private String generateCommandLaunchScript(RunJobParameters parameters,
      TaskType taskType) throws IOException {
    File file = File.createTempFile(taskType.name() + "-launch-script", ".sh");
    FileWriter fw = new FileWriter(file);

    fw.append("#!/bin/bash\n");

    addHdfsClassPathIfNeeded(parameters, fw);

    // TODO, for distributed training
    // 1) need to check DNS entry name
    // 2) need to check how COMPONENT_ID passed to containers.

    // For master node (worker, and index = 0)
    if (taskType == TaskType.WORKER) {
      fw.append("if [[ $YALP_TASK_TYPE == 'WORKER' && $YALP_TASK_INDEX == '0' ]]; then\n");
      // Do we need tensorboard?
      if (parameters.isTensorboardEnabled()) {
        int tensorboardPort = clientContext.getTaskNetworkPortManager().getPort(
            parameters.getJobName(), "tensorboard", 0);
        // Run tensorboard at the background
        fw.append(
            "tensorboard --port " + tensorboardPort + " --logdir " + parameters
                .getOutput() + "&\n");
      }
      fw.append("fi\n");
    }

    // Print launch command
    if (taskType.equals(TaskType.WORKER)) {
      fw.append(parameters.getWorkerLaunchCmd() + '\n');
    } else if (taskType.equals(TaskType.PS)) {
      fw.append(parameters.getPSLaunchCmd() + '\n');
    }

    fw.close();
    return file.getAbsolutePath();
  }

  private String getScriptFileName(TaskType taskType) {
    return "run-" + taskType.name() + ".sh";
  }

  private void handleLaunchCommand(RunJobParameters parameters,
      TaskType taskType, Component component) throws IOException {
    // Get staging area directory
    Path stagingDir =
        clientContext.getRemoteDirectoryManager().getAndCreateJobStagingArea(
            parameters.getJobName());

    // Generate script file in the local disk
    String localScriptFile = generateCommandLaunchScript(parameters, taskType);
    FileSystem fs = FileSystem.get(clientContext.getConfiguration());

    // Upload to remote FS under staging area
    String destScriptFileName = getScriptFileName(taskType);
    Path destScriptPath = new Path(stagingDir, destScriptFileName);
    fs.copyFromLocalFile(new Path(localScriptFile), destScriptPath);
    FileStatus fileStatus = fs.getFileStatus(destScriptPath);
    LOG.info("Uploaded file path = " + fileStatus.getPath());

    // Set it to component's files list
    component.getConfiguration().getFiles().add(new ConfigFile().srcFile(
        fileStatus.getPath().toUri().toString()).destFile(destScriptFileName)
        .type(YAML));

    component.setLaunchCommand("bash -c " + destScriptFileName);
  }

  private void addCommonEnvironments(Component component, TaskType taskType) {
    Map<String, String> envs = component.getConfiguration().getEnv();
    envs.put(Constants.YALP_TASK_INDEX_ENV, ServiceApiConstants.COMPONENT_ID);
    envs.put(Constants.YALP_TASK_TYPE_ENV, taskType.name());
  }

  private Service createServiceByParameters(RunJobParameters parameters)
      throws IOException {
    Service service = new Service();
    service.setName(parameters.getJobName());
    service.setVersion(String.valueOf(System.currentTimeMillis()));
    service.setArtifact(new Artifact().type(Artifact.TypeEnum.DOCKER)
        .id(parameters.getDockerImageName()));


    Component workerComponent = new Component();
    addCommonEnvironments(workerComponent, TaskType.WORKER);
    workerComponent.setName(Constants.WORKER_COMPONENT_NAME);
    workerComponent.setNumberOfContainers((long) parameters.getNumWorkers());
    workerComponent.setResource(
        getServiceResourceFromYarnResource(parameters.getWorkerResource()));
    handleLaunchCommand(parameters, TaskType.WORKER, workerComponent);
    service.addComponent(workerComponent);

    if (parameters.getNumPS() > 0) {
      Component psComponent = new Component();
      psComponent.setName(Constants.PS_COMPONENT_NAME);
      addCommonEnvironments(workerComponent, TaskType.PS);
      psComponent.setNumberOfContainers((long) parameters.getNumPS());
      psComponent.setResource(
          getServiceResourceFromYarnResource(parameters.getPsResource()));
      handleLaunchCommand(parameters, TaskType.PS, psComponent);
      service.addComponent(psComponent);
    }
    return service;
  }

  /**
   * Run a job by given parameters, returns when job sucessfully submitted to
   * RM.
   * @param parameters parameters
   * @throws IOException
   * @throws YarnException
   */
  public Service runJob(RunJobParameters parameters)
      throws IOException, YarnException {
    Service service = createServiceByParameters(parameters);
    ServiceClient serviceClient = clientContext.getServiceClient();
    serviceClient.actionCreate(service);
    return service;
  }
}
