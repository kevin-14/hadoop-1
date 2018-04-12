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

public class ModelServeParameters extends RunParameters {
  private int numServingTasks;
  private Resource servingTaskResource;
  private String servingLaunchCommandline;
  private String servingFramework;

  @Override
  protected void printUsages(Options options) {
    new HelpFormatter().printHelp("model serve", options);
  }

  @Override
  public void updateParametersByParsedCommandline(CommandLine parsedCommandLine,
      Options options, ClientContext clientContext)
      throws ParseException, IOException, YarnException {
    numServingTasks = 1;
    String nServingTasksStr = parsedCommandLine.getOptionValue(CliConstants.N_SERVING_TASKS);
    if (nServingTasksStr != null) {
      numServingTasks = Integer.parseInt(nServingTasksStr);
    }

    String servingTaskResourceStr = parsedCommandLine.getOptionValue(
        CliConstants.SERVING_RES);
    if (servingTaskResourceStr == null) {
      printUsages(options);
      throw new ParseException("--" + CliConstants.SERVING_RES + " is absent.");
    }
    servingTaskResource = CliUtils.createResourceFromString(
        servingTaskResourceStr, clientContext.getOrCreateYarnClient());

    servingLaunchCommandline = parsedCommandLine.getOptionValue(
        CliConstants.SERVING_LAUNCH_CMD);

    servingFramework = parsedCommandLine.getOptionValue(
        CliConstants.SERVING_FRAMEWORK);
    super.updateParametersByParsedCommandline(parsedCommandLine, options,
        clientContext);
  }

  public int getNumServingTasks() {
    return numServingTasks;
  }

  public Resource getServingTaskResource() {
    return servingTaskResource;
  }

  public String getServingLaunchCommandline() {
    return servingLaunchCommandline;
  }

  public String getServingFramework() {
    return servingFramework;
  }
}
