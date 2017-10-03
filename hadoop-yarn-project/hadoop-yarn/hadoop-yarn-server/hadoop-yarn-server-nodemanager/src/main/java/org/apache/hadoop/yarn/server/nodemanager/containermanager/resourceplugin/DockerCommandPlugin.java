package org.apache.hadoop.yarn.server.nodemanager.containermanager.resourceplugin;

import org.apache.hadoop.yarn.server.nodemanager.containermanager.linux.runtime.docker.DockerRunCommand;

/**
 * Interface to make different resource plugins (e.g. GPU) can update docker run
 * command without adding logic to Docker runtime.
 */
public interface DockerCommandPlugin {
  /**
   * Update docker run command
   * @param dockerRunCommand docker run command
   */
  void updateDockerRunCommand(DockerRunCommand dockerRunCommand);

  // Add support to other docker command when required.
}
