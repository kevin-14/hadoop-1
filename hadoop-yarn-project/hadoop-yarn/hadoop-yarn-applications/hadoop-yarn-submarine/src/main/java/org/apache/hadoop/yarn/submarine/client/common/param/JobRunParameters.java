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

package org.apache.hadoop.yarn.submarine.client.common.param;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.hadoop.yarn.api.records.Resource;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.apache.hadoop.yarn.submarine.client.cli.CliConstants;
import org.apache.hadoop.yarn.submarine.client.common.CliUtils;
import org.apache.hadoop.yarn.submarine.client.common.ClientContext;

import java.io.IOException;

/**
 * Parameters used to run a job
 */
public class JobRunParameters extends RunParameters {
  private String input;
  private String jobDir;

  private int numWorkers;
  private int numPS;
  private Resource workerResource;
  private Resource psResource;
  private boolean tensorboardEnabled;
  private String workerLaunchCmd;
  private String psLaunchCmd;

  @Override
  public void printUsages(Options options) {
    new HelpFormatter().printHelp("job run", options);
  }

  @Override
  public void updateParametersByParsedCommandline(CommandLine parsedCommandLine,
      Options options, ClientContext clientContext)
      throws ParseException, IOException, YarnException {

    String input = parsedCommandLine.getOptionValue(CliConstants.INPUT);
    String jobDir = parsedCommandLine.getOptionValue(CliConstants.JOB_DIR);
    int nWorkers = 1;
    if (parsedCommandLine.getOptionValue(CliConstants.N_WORKERS) != null) {
      nWorkers = Integer.parseInt(
          parsedCommandLine.getOptionValue(CliConstants.N_WORKERS));
    }

    int nPS = 0;
    if (parsedCommandLine.getOptionValue(CliConstants.N_PS) != null) {
      nPS = Integer.parseInt(
          parsedCommandLine.getOptionValue(CliConstants.N_PS));
    }

    String workerResourceStr = parsedCommandLine.getOptionValue(
        CliConstants.WORKER_RES);
    if (workerResourceStr == null) {
      printUsages(options);
      throw new ParseException("--" + CliConstants.WORKER_RES + " is absent.");
    }
    Resource workerResource = CliUtils.createResourceFromString(
        workerResourceStr, clientContext.getOrCreateYarnClient());

    Resource psResource = null;
    if (nPS > 0) {
      String psResourceStr = parsedCommandLine.getOptionValue(CliConstants.PS_RES);
      if (psResourceStr == null) {
        printUsages(options);
        throw new ParseException("--" + CliConstants.PS_RES + " is absent.");
      }
      psResource = CliUtils.createResourceFromString(psResourceStr,
          clientContext.getOrCreateYarnClient());
    }

    boolean tensorboard = true;
    if (parsedCommandLine.getOptionValue(CliConstants.TENSORBOARD) != null) {
      tensorboard = Boolean.parseBoolean(
          parsedCommandLine.getOptionValue(CliConstants.TENSORBOARD));
    }

    String workerLaunchCmd = parsedCommandLine.getOptionValue(
        CliConstants.WORKER_LAUNCH_CMD);
    String psLaunchCommand = parsedCommandLine.getOptionValue(
        CliConstants.PS_LAUNCH_CMD);

    this.setInput(input).setJobDir(jobDir).setNumPS(nPS).setNumWorkers(nWorkers)
        .setPSLaunchCmd(psLaunchCommand).setWorkerLaunchCmd(workerLaunchCmd)
        .setPsResource(psResource).setWorkerResource(workerResource)
        .setTensorboardEnabled(tensorboard);

    super.updateParametersByParsedCommandline(parsedCommandLine,
        options, clientContext);
  }

  public String getInput() {
    return input;
  }

  public JobRunParameters setInput(String input) {
    this.input = input;
    return this;
  }

  public String getJobDir() {
    return jobDir;
  }

  public JobRunParameters setJobDir(String jobDir) {
    this.jobDir = jobDir;
    return this;
  }

  public int getNumWorkers() {
    return numWorkers;
  }

  public JobRunParameters setNumWorkers(int numWorkers) {
    this.numWorkers = numWorkers;
    return this;
  }

  public int getNumPS() {
    return numPS;
  }

  public JobRunParameters setNumPS(int numPS) {
    this.numPS = numPS;
    return this;
  }

  public Resource getWorkerResource() {
    return workerResource;
  }

  public JobRunParameters setWorkerResource(Resource workerResource) {
    this.workerResource = workerResource;
    return this;
  }

  public Resource getPsResource() {
    return psResource;
  }

  public JobRunParameters setPsResource(Resource psResource) {
    this.psResource = psResource;
    return this;
  }

  public boolean isTensorboardEnabled() {
    return tensorboardEnabled;
  }

  public JobRunParameters setTensorboardEnabled(boolean tensorboardEnabled) {
    this.tensorboardEnabled = tensorboardEnabled;
    return this;
  }

  public String getWorkerLaunchCmd() {
    return workerLaunchCmd;
  }

  public JobRunParameters setWorkerLaunchCmd(String workerLaunchCmd) {
    this.workerLaunchCmd = workerLaunchCmd;
    return this;
  }

  public String getPSLaunchCmd() {
    return psLaunchCmd;
  }

  public JobRunParameters setPSLaunchCmd(String psLaunchCmd) {
    this.psLaunchCmd = psLaunchCmd;
    return this;
  }
}
