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

package org.apache.hadoop.yarn.submarine.client.common;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.yarn.submarine.client.common.fs.RemoteDirectoryManager;
import org.apache.hadoop.yarn.submarine.client.common.network.RandomTaskNetworkPortManagerImpl;
import org.apache.hadoop.yarn.submarine.client.common.network.TaskNetworkPortManager;
import org.apache.hadoop.yarn.submarine.client.common.param.JobRunParameters;
import org.apache.hadoop.yarn.submarine.client.monitor.JobMonitor;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.service.client.ServiceClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientContext {
  private Configuration conf = new YarnConfiguration();

  private RemoteDirectoryManager remoteDirectoryManager;
  private TaskNetworkPortManager taskNetworkPortManager;
  private YarnClient yarnClient;
  private ServiceClient serviceClient;
  private Map<String, JobRunParameters> cachedRunJobParameters =
      new ConcurrentHashMap<>();
  private JobMonitor jobMonitor;
  private boolean verbose;

  public ClientContext() {
    this.taskNetworkPortManager =
        new RandomTaskNetworkPortManagerImpl();
    this.jobMonitor = new JobMonitor();
  }

  public synchronized YarnClient getOrCreateYarnClient() {
    if (yarnClient == null) {
      yarnClient = YarnClient.createYarnClient();
      yarnClient.init(conf);
      yarnClient.start();
    }
    return yarnClient;
  }

  public Configuration getConfiguration() {
    return conf;
  }

  public void setConfiguration(Configuration conf) {
    this.conf = conf;
  }

  public TaskNetworkPortManager getTaskNetworkPortManager() {
    return taskNetworkPortManager;
  }

  public RemoteDirectoryManager getRemoteDirectoryManager() {
    return remoteDirectoryManager;
  }

  public void setRemoteDirectoryManager(
      RemoteDirectoryManager remoteDirectoryManager) {
    this.remoteDirectoryManager = remoteDirectoryManager;
  }

  public synchronized ServiceClient getServiceClient() {
    if (serviceClient == null) {
      serviceClient = new ServiceClient();
      serviceClient.init(conf);
      serviceClient.start();
    }
    return serviceClient;
  }

  public JobRunParameters getRunJobParameters(String jobName) {
    return cachedRunJobParameters.get(jobName);
  }

  public void addRunJobParameters(String jobName, JobRunParameters parameters) {
    cachedRunJobParameters.put(jobName, parameters);
  }

  public JobMonitor getJobMonitor() {
    return jobMonitor;
  }


  public boolean isVerbose() {
    return verbose;
  }

  public void setVerbose(boolean verbose) {
    this.verbose = verbose;
  }
}
