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
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.apache.hadoop.yarn.submarine.client.cli.CliConstants;
import org.apache.hadoop.yarn.submarine.client.common.ClientContext;

import java.io.IOException;

/**
 * Base class of all parameters.
 */
public abstract class BaseParameters {
  private String name;

  protected abstract void printUsages(Options options);

  public void updateParametersByParsedCommandline(CommandLine parsedCommandLine,
      Options options, ClientContext clientContext)
      throws ParseException, IOException, YarnException {
    String name = parsedCommandLine.getOptionValue(CliConstants.NAME);
    if (name == null) {
      printUsages(options);
      throw new ParseException("--name is absent");
    }

    boolean verbose = false;
    if (parsedCommandLine.hasOption(CliConstants.VERBOSE)) {
      verbose = true;
    }
    clientContext.setVerbose(verbose);
    this.setJobName(name);
  }

  public String getJobName() {
    return name;
  }

  public BaseParameters setJobName(String name) {
    this.name = name;
    return this;
  }
}
