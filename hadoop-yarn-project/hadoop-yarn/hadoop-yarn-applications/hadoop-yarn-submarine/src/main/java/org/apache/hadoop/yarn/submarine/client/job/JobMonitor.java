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

import org.apache.hadoop.yarn.submarine.client.common.ClientContext;
import org.apache.hadoop.yarn.submarine.client.common.param.JobRunParameters;
import org.apache.hadoop.yarn.submarine.client.common.TrainingJobStatus;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.apache.hadoop.yarn.service.api.records.Service;
import org.apache.hadoop.yarn.service.api.records.ServiceState;
import org.apache.hadoop.yarn.service.client.ServiceClient;

import java.io.IOException;

/**
 * Monitor status of job
 */
public class JobMonitor {
  /**
   * Returns status of training job.
   * @return TrainingJobStatus
   */
  public TrainingJobStatus getTrainingJobStatus(String jobName, ClientContext clientContext)
      throws IOException, YarnException {
    ServiceClient serviceClient = clientContext.getServiceClient();
    Service serviceSpec = serviceClient.getStatus(jobName);
    TrainingJobStatus jobStatus = TrainingJobStatus.fromServiceSepc(serviceSpec,
        clientContext);
    return jobStatus;
  }

  /**
   * Continue wait and print status if job goes to ready or final state.
   * @param jobName
   * @param clientContext
   * @throws IOException
   * @throws YarnException
   */
  public void waitTrainingJobReadyOrFinal(String jobName,
      ClientContext clientContext)
      throws IOException, YarnException, InterruptedException {
    JobRunParameters parameters = clientContext.getRunJobParameters(jobName);

    // Wait 15 sec between each fetch.
    int waitSec = 15;
    TrainingJobStatus js;
    while (true) {
      js = getTrainingJobStatus(jobName, clientContext);
      ServiceState jobState = js.getState();
      js.nicePrint(System.err);

      if (jobState == ServiceState.FAILED || jobState == ServiceState.STOPPED) {
        break;
      }

      if (parameters.isTensorboardEnabled()) {
        if (js.getTensorboardLink().startsWith("http")
            && jobState == ServiceState.STABLE) {
          break;
        }
      } else if (jobState == ServiceState.STABLE) {
        break;
      }

      Thread.sleep(waitSec * 1000);
    }
  }
}
