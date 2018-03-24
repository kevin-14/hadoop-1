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

package org.apache.hadoop.yarn.applications.yalp.client.cli;

import org.apache.hadoop.yarn.applications.yalp.client.cli.job.RunJobCli;

import java.util.Arrays;

public class Cli {
  private static void printHelp() {
    // TODO;
  }

  public static void main(String[] args) throws Exception {
    if (args.length < 2) {
      printHelp();
      throw new IllegalArgumentException("TODO");
    }

    String[] moduleArgs = Arrays.copyOfRange(args, 2, args.length);

    if (args[0].equals("job")) {
      String subCmd = args[1];
      if (subCmd.equals(CliConstants.RUN)) {
        new RunJobCli().run(moduleArgs);
      }
      // else TODO
    } else if (args[0].equals("model")) {
      new ModelCli().run(Arrays.copyOfRange(args, 1, args.length));
    }
  }
}
