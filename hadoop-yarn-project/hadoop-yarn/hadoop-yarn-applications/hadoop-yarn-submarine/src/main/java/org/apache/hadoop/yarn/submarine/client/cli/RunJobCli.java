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

package org.apache.hadoop.yarn.submarine.client.cli;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.hadoop.yarn.submarine.client.job.JobSubmitter;
import org.apache.hadoop.yarn.submarine.client.common.ClientContext;
import org.apache.hadoop.yarn.submarine.client.common.param.JobRunParameters;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.apache.hadoop.yarn.service.api.records.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.apache.hadoop.yarn.service.utils.ServiceApiUtil.jsonSerDeser;

public class RunJobCli extends AbstractCli{
  private static final Logger LOG =
      LoggerFactory.getLogger(RunJobCli.class);

  private Options options;
  private Service serviceSpec;

  private JobRunParameters parameters = new JobRunParameters();

  public RunJobCli(ClientContext cliContext) {
    super(cliContext);
    options = generateOptions();
  }

  public void printUsages() {
    parameters.printUsages(options);
  }

  private Options generateOptions() {
    Options options = new Options();
    options.addOption(CliConstants.NAME, true, "Name of the job");
    options.addOption(CliConstants.INPUT, true,
        "Input of the job, could be local or other FS directory");
    options.addOption(CliConstants.JOB_DIR, true,
        "Training output directory of the job, "
            + "could be local or other FS directory. This typically includes "
            + "checkpoint files and exported model ");
    options.addOption(CliConstants.SAVEDMODEL_PATH, true,
        "Model exported path (savedmodel) of the job, which is needed when "
            + "exported model is not placed under ${job_dir}"
            + "could be local or other FS directory. This will be used to serve.");
    options.addOption(CliConstants.N_WORKERS, true,
        "Numnber of worker tasks of the job, by default it's 1");
    options.addOption(CliConstants.N_PS, true,
        "Number of PS tasks of the job, by default it's 0");
    options.addOption(CliConstants.WORKER_RES, true,
        "Resource of each worker, for example "
            + "memory-mb=2048,vcores=2,yarn.io/gpu=2");
    options.addOption(CliConstants.PS_RES, true,
        "Resource of each PS, for example "
            + "memory-mb=2048,vcores=2,yarn.io/gpu=2");
    options.addOption(CliConstants.DOCKER_IMAGE, true, "Docker image name/tag");
    options.addOption(CliConstants.QUEUE, true,
        "Name of queue to run the job, by default it uses default queue");
    options.addOption(CliConstants.TENSORBOARD, true, "Should we run TensorBoard"
        + " for this job? By default it's true");
    options.addOption(CliConstants.WORKER_LAUNCH_CMD, true,
        "Commandline of worker, arguments will be "
            + "directly used to launch the worker");
    options.addOption(CliConstants.PS_LAUNCH_CMD, true,
        "Commandline of worker, arguments will be "
            + "directly used to launch the PS");
    options.addOption(CliConstants.ENV, true,
        "Common environment variable of worker/ps");
    options.addOption(CliConstants.VERBOSE, false,
        "Print verbose log for troubleshooting");
    return options;
  }

  private void parseCommandLineAndGetRunJobParameters(String[] args)
      throws ParseException, IOException, YarnException {
    // Do parsing
    GnuParser parser = new GnuParser();
    CommandLine cli = parser.parse(options, args);
    parameters.updateParametersByParsedCommandline(cli, options, clientContext);
  }

  @Override
  public void run(String[] args)
      throws ParseException, IOException, YarnException, InterruptedException {
    parseCommandLineAndGetRunJobParameters(args);

    clientContext.addRunJobParameters(parameters.getName(), parameters);

    JobSubmitter jobSubmitter = new JobSubmitter(clientContext);
    serviceSpec = jobSubmitter.runJob(parameters);

    if (clientContext.isVerbose()) {
      LOG.info("Service Spec:");
      LOG.info(jsonSerDeser.toJson(serviceSpec));
    }

    clientContext.getJobMonitor().waitTrainingJobReadyOrFinal(
        parameters.getName(), clientContext);
  }

  @VisibleForTesting
  Service getServiceSpec() {
    return serviceSpec;
  }

  @VisibleForTesting
  public JobRunParameters getRunJobParameters() {
    return parameters;
  }
}
