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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.apache.hadoop.yarn.submarine.client.common.ClientContext;
import org.apache.hadoop.yarn.submarine.client.common.param.ModelServeParameters;

import java.io.IOException;
import java.util.Arrays;

public class ModelCli extends AbstractCli {
  private static Options OPTIONS = generateOptions();

  public ModelCli(ClientContext cliContext) {
    super(cliContext);
  }

  private static Options generateOptions() {
    Options options = new Options();
    options.addOption(CliConstants.NAME, true, "Name of the model serving job");
    options.addOption(CliConstants.SAVEDMODEL_PATH, true,
        "Model exported path (savedmodel) of the job, which is needed when "
            + "exported model is not placed under ${job_dir}"
            + "could be local or other FS directory. This will be used to serve.");
    options.addOption(CliConstants.DOCKER_IMAGE, true, "Docker image name/tag");
    options.addOption(CliConstants.QUEUE, true,
        "Name of queue to run the job, by default it uses default queue");
    options.addOption(CliConstants.ENV, true,
        "Other environment variables");
    options.addOption(CliConstants.VERBOSE, false,
        "Print verbose log for troubleshooting");

    options.addOption(CliConstants.N_SERVING_TASKS, true,
        "Numnber of replicas of the model serving job, by default it's 1");
    options.addOption(CliConstants.SERVING_RES, true,
        "Resource of each serving task, for example "
            + "memory-mb=2048,vcores=2,yarn.io/gpu=2");
    options.addOption(CliConstants.SERVING_LAUNCH_CMD, true,
        "Commandline of serving service, arguments will be "
            + "directly used to launch the worker. If this is not specified,"
            + " framework will generate command line according to framework");
    options.addOption(CliConstants.SERVING_FRAMEWORK, true,
        "Which framework is used to do serving, if not specified, use "
            + "simple_tensorflow_serving");
    return options;
  }

  @Override
  public void run(String[] args)
      throws ParseException, IOException, YarnException {
    String action = args[0];

    if (action.equals(CliConstants.SERVE)) {
      String[] toParseArgs = Arrays.copyOfRange(args, 2, args.length);

      // Do parsing
      GnuParser parser = new GnuParser();
      CommandLine cli = parser.parse(OPTIONS, toParseArgs);

      ModelServeParameters modelServeParameters = new ModelServeParameters();
      modelServeParameters.updateParametersByParsedCommandline(cli, OPTIONS,
          clientContext);

    } else {
      throw new IllegalArgumentException("Wrong action for model command");
    }
  }
}
