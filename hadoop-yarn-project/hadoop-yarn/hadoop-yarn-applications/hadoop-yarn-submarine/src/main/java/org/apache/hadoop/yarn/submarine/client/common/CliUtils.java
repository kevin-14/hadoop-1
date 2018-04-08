/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. See accompanying LICENSE file.
 */

package org.apache.hadoop.yarn.submarine.client.common;

import org.apache.hadoop.yarn.submarine.client.cli.CliConstants;
import org.apache.hadoop.yarn.submarine.client.common.fs.RemoteDirectoryManager;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class CliUtils {
  /**
   * Replace patterns inside cli
   * @return launch command after pattern replace
   */
  public static String replacePatternsInLaunchCommand(String specifiedCli,
      RunJobParameters runJobParameters,
      RemoteDirectoryManager directoryManager) throws IOException {
    String jobDir = runJobParameters.getJobDir();
    if (null == jobDir) {
      jobDir = directoryManager.getAndCreateJobDir(
          runJobParameters.getJobName()).toString();
    }

    String input = runJobParameters.getInput();
    String savedModelDir = runJobParameters.getSavedModelPath();
    if (null == savedModelDir) {
      savedModelDir = jobDir;
    }

    Map<String, String> replacePattern = new HashMap<>();
    if (jobDir != null) {
      replacePattern.put("%" + CliConstants.JOB_DIR + "%", jobDir);
    }
    if (input != null) {
      replacePattern.put("%" + CliConstants.INPUT + "%", input);
    }
    if (savedModelDir != null) {
      replacePattern.put("%" + CliConstants.SAVEDMODEL_PATH + "%",
          savedModelDir);
    }

    String newCli = specifiedCli;
    for (Map.Entry<String, String> replace : replacePattern.entrySet()) {
      newCli = newCli.replace(replace.getKey(), replace.getValue());
    }

    return newCli;
  }
}
