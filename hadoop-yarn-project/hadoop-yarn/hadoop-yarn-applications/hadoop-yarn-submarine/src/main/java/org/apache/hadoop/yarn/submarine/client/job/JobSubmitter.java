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

package org.apache.hadoop.yarn.submarine.client.job;

import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.yarn.submarine.client.common.CliUtils;
import org.apache.hadoop.yarn.submarine.client.common.ClientContext;
import org.apache.hadoop.yarn.submarine.client.common.Constants;
import org.apache.hadoop.yarn.submarine.client.common.Envs;
import org.apache.hadoop.yarn.submarine.client.common.param.JobRunParameters;
import org.apache.hadoop.yarn.submarine.client.common.TaskType;
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

  private void addHdfsClassPathIfNeeded(JobRunParameters parameters,
      FileWriter fw) throws IOException {
    // Find envs to use HDFS
    String hdfsHome = null;
    String hadoopConfDir = null;
    String javaHome = null;

    boolean hadoopEnv = false;

    for (String envar : parameters.getEnvars()) {
      if (envar.startsWith(Envs.HADOOP_HDFS_HOME + "=")) {
        hdfsHome = envar;
        hadoopEnv = true;
      } else if (envar.startsWith(Envs.HADOOP_CONF_DIR + "=")) {
        hadoopConfDir = envar;
        hadoopEnv = true;
      } else if (envar.startsWith(Envs.JAVA_HOME + "=")) {
        javaHome = envar;
      }
    }

    if ((parameters.getInput() != null && parameters.getInput().contains(
        "hdfs://")) || (parameters.getJobDir() != null && parameters.getJobDir()
        .contains("hdfs://")) || hadoopEnv) {
      // HDFS is asked either in input or output, set LD_LIBRARY_PATH
      // and classpath

      if (hdfsHome != null) {
        fw.append("export " + hdfsHome + "\n");
        fw.append("export PATH=$PATH:$" + Envs.HADOOP_HDFS_HOME + "/bin/\n");
      }
      if (hadoopConfDir != null) {
        fw.append("export " + hadoopConfDir + "\n");
      }
      if (javaHome != null) {
        fw.append("export " + javaHome + "\n");
        fw.append("export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:"
            + "$JAVA_HOME/lib/amd64/server\n");
      }
      fw.append("export CLASSPATH=`hadoop classpath --glob`\n");
    }

    // DEBUG
    fw.append("echo $CLASSPATH\n");
    fw.append("echo $JAVA_HOME\n");
    fw.append("echo $LD_LIBRARY_PATH\n");
    fw.append("echo $HADOOP_HDFS_HOME\n");
  }

  /*
   * Generate a command launch script on local disk, returns patch to the script
   */
  private String generateCommandLaunchScript(JobRunParameters parameters,
      TaskType taskType) throws IOException {
    File file = File.createTempFile(taskType.name() + "-launch-script", ".sh");
    FileWriter fw = new FileWriter(file);

    fw.append("#!/bin/bash\n");

    addHdfsClassPathIfNeeded(parameters, fw);

    // TODO, for distributed training
    // 1) need to check DNS entry name
    // 2) need to check how COMPONENT_ID passed to containers.

    // For primary_worker
    if (taskType == TaskType.PRIMARY_WORKER) {
      // Do we need tensorboard?
      if (parameters.isTensorboardEnabled()) {
        int tensorboardPort = clientContext.getTaskNetworkPortManager().getPort(
            parameters.getName(), "tensorboard", 0);
        // Run tensorboard at the background
        fw.append(
            "tensorboard --port " + tensorboardPort + " --logdir " + parameters
                .getJobDir() + " &\n");
      }
    }

    // Print launch command
    if (taskType.equals(TaskType.WORKER) || taskType.equals(
        TaskType.PRIMARY_WORKER)) {
      String afterReplace = CliUtils.replacePatternsInLaunchCommand(
          parameters.getWorkerLaunchCmd(), parameters,
          clientContext.getRemoteDirectoryManager());

      fw.append(afterReplace + '\n');

      if (clientContext.isVerbose()) {
        LOG.info("Worker command before commandline replace=[" + parameters
            .getWorkerLaunchCmd() + "] after replace=[" + afterReplace + "]");
      }

      parameters.setWorkerLaunchCmd(afterReplace);
    } else if (taskType.equals(TaskType.PS)) {
      String afterReplace = CliUtils.replacePatternsInLaunchCommand(
          parameters.getPSLaunchCmd(), parameters,
          clientContext.getRemoteDirectoryManager());

      if (clientContext.isVerbose()) {
        LOG.info("PS command before commandline replace=[" + parameters
            .getPSLaunchCmd() + "] after replace=[" + afterReplace + "]");
      }

      fw.append(afterReplace + '\n');

      parameters.setPSLaunchCmd(afterReplace);
    }

    fw.close();
    return file.getAbsolutePath();
  }

  private String getScriptFileName(TaskType taskType) {
    return "run-" + taskType.name() + ".sh";
  }

  private void handleLaunchCommand(JobRunParameters parameters,
      TaskType taskType, Component component) throws IOException {
    // Get staging area directory
    Path stagingDir =
        clientContext.getRemoteDirectoryManager().getAndCreateJobStagingArea(
            parameters.getName());

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
        .type(ConfigFile.TypeEnum.STATIC));

    // TODO, file should not be automatically put to ./conf
    component.setLaunchCommand("bash -c ./localized/" + destScriptFileName);
  }

  private void addCommonEnvironments(Component component, TaskType taskType) {
    Map<String, String> envs = component.getConfiguration().getEnv();
    envs.put(Envs.TASK_INDEX_ENV, ServiceApiConstants.COMPONENT_ID);
    envs.put(Envs.TASK_TYPE_ENV, taskType.name());
  }

  private void addWorkerComponent(Service service,
      JobRunParameters parameters, TaskType taskType) throws IOException {
    Component workerComponent = new Component();
    addCommonEnvironments(workerComponent, taskType);

    if (taskType.equals(TaskType.PRIMARY_WORKER)) {
      workerComponent.setName(Constants.PRIMARY_WORKER_COMPONENT_NAME);
      workerComponent.setNumberOfContainers(1L);
    } else{
      workerComponent.setName(Constants.WORKER_COMPONENT_NAME);
      workerComponent.setNumberOfContainers(
          (long) parameters.getNumWorkers() - 1);
    }

    workerComponent.setResource(
        getServiceResourceFromYarnResource(parameters.getWorkerResource()));
    handleLaunchCommand(parameters, taskType, workerComponent);
    workerComponent.setRestartPolicy(Component.RestartPolicyEnum.NEVER);
    service.addComponent(workerComponent);
  }

  // Handle worker and primary_worker.
  private void addWorkerComponents(Service service, JobRunParameters parameters)
      throws IOException {
    addWorkerComponent(service, parameters, TaskType.PRIMARY_WORKER);

    if (parameters.getNumWorkers() > 1) {
      addWorkerComponent(service, parameters, TaskType.WORKER);
    }
  }

  private Service createServiceByParameters(JobRunParameters parameters)
      throws IOException {
    Service service = new Service();
    service.setName(parameters.getName());
    service.setVersion(String.valueOf(System.currentTimeMillis()));
    service.setArtifact(new Artifact().type(Artifact.TypeEnum.DOCKER)
        .id(parameters.getDockerImageName()));

    if (parameters.getEnvars() != null) {
      for (String envarPair : parameters.getEnvars()) {
        if (envarPair.contains("=")) {
          int idx = envarPair.indexOf('=');
          String key = envarPair.substring(0, idx);
          String value = envarPair.substring(idx + 1);
          service.getConfiguration().getEnv().put(key, value);
        } else{
          // No "=" found so use the whole key
          service.getConfiguration().getEnv().put(envarPair, "");
        }
      }
    }

    addWorkerComponents(service, parameters);

    if (parameters.getNumPS() > 0) {
      Component psComponent = new Component();
      psComponent.setName(Constants.PS_COMPONENT_NAME);
      addCommonEnvironments(psComponent, TaskType.PS);
      psComponent.setNumberOfContainers((long) parameters.getNumPS());
      psComponent.setRestartPolicy(Component.RestartPolicyEnum.NEVER);
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
  public Service runJob(JobRunParameters parameters)
      throws IOException, YarnException {
    Service service = createServiceByParameters(parameters);
    ServiceClient serviceClient = clientContext.getServiceClient();
    serviceClient.actionCreate(service);
    return service;
  }
}
