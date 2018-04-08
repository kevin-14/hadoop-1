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

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.yarn.submarine.client.common.ClientContext;
import org.apache.hadoop.yarn.submarine.client.common.fs.DefaultRemoteDirectoryManager;
import org.apache.hadoop.yarn.conf.YarnConfiguration;

import java.util.Arrays;

public class Cli {
  private static void printHelp() {
    // TODO;
  }

  private static ClientContext getClientContext() {
    Configuration conf = new YarnConfiguration();
    ClientContext clientContext = new ClientContext();
    clientContext.setConfiguration(conf);
    clientContext.setRemoteDirectoryManager(
        new DefaultRemoteDirectoryManager(clientContext));
    return clientContext;
  }

  public static void main(String[] args) throws Exception {
    if (args.length < 2) {
      printHelp();
      // TODO
      throw new IllegalArgumentException("Bad parameters <TODO>");
    }

    String[] moduleArgs = Arrays.copyOfRange(args, 2, args.length);
    ClientContext clientContext = getClientContext();

    if (args[0].equals("job")) {
      String subCmd = args[1];
      if (subCmd.equals(CliConstants.RUN)) {
        new RunJobCli(clientContext).run(moduleArgs);
      } else {
        throw new IllegalArgumentException("Unknown option for job");
      }
    } else if (args[0].equals("model")) {
      new ModelCli(clientContext).run(Arrays.copyOfRange(args, 1, args.length));
    } else {
      printHelp();
      throw new IllegalArgumentException("Bad parameters <TODO>");
    }
  }
}
