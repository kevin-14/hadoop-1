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

package org.apache.hadoop.yarn.submarine.client.common.fs;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.yarn.submarine.client.cli.CliConstants;
import org.apache.hadoop.yarn.submarine.client.common.ClientContext;

import java.io.IOException;

/**
 * Manages remote directories for staging, log, etc.
 * TODO, need to properly handle permission
 */
public class DefaultRemoteDirectoryManager implements RemoteDirectoryManager {
  FileSystem fs;

  public DefaultRemoteDirectoryManager(ClientContext context) {
    try {
      this.fs = FileSystem.get(context.getConfiguration());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Path getAndCreateJobStagingArea(String jobName) throws IOException {
    Path staging = new Path(getJobRootFolder(jobName), "staging");
    createFolderIfNotExist(staging);
    return staging;
  }

  @Override
  public Path getAndCreateJobDir(String jobName) throws IOException {
    Path jobStagingArea = getAndCreateJobStagingArea(jobName);

    Path jobDir = new Path(getAndCreateJobStagingArea(jobName),
        CliConstants.JOB_DIR);
    createFolderIfNotExist(jobDir);
    return jobDir;
  }

  private Path getJobRootFolder(String jobName) throws IOException {
    return new Path("submarine", jobName);
  }

  private void createFolderIfNotExist(Path path) throws IOException {
    if (!fs.exists(path)) {
      if (!fs.mkdirs(path)) {
        throw new IOException("Failed to create folder=" + path);
      }
    }
  }

  private Path getAndCreateJobRootFolder(String jobName) throws IOException {
    Path root = getJobRootFolder(jobName);
    createFolderIfNotExist(root);
    return root;
  }
}
