/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.yarn.server.nodemanager.containermanager.resourceplugin.gpu;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.server.nodemanager.Context;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.linux.runtime.docker.DockerRunCommand;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.resourceplugin.DockerCommandPlugin;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.runtime.ContainerExecutionException;

import java.io.StringWriter;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class GpuDockerCommandPlugin implements DockerCommandPlugin {
  final static Log LOG = LogFactory
      .getLog(GpuDockerCommandPlugin.class);

  private Context nmContext;
  private Configuration conf;
  private Map<String, Set<String>> additionalCommands = null;

  // Known option
  private String DEVICE_OPTION = "--device";
  private String VOLUME_DRIVER_OPTION = "--volume-driver";
  private String MOUNT_RO_OPTION = "--volume";

  public GpuDockerCommandPlugin(Configuration conf, Context nmContext) {
    this.nmContext = nmContext;
    this.conf = conf;
  }

  // Get value from key=value
  // Throw exception if '=' not found
  private String getValue(String input) throws IllegalArgumentException {
    int index = input.indexOf('=');
    if (index < 0) {
      throw new IllegalArgumentException(
          "Failed to locate '=' from input=" + input);
    }
    return input.substring(index + 1);
  }

  private void addToCommand(String key, String value) {
    if (!additionalCommands.containsKey(key)) {
      additionalCommands.put(key, new HashSet<>());
    }
    additionalCommands.get(key).add(value);
  }

  private void init() throws ContainerExecutionException {
    String endpoint = conf.get(YarnConfiguration.NVIDIA_DOCKER_PLUGIN_ENDPOINT,
        YarnConfiguration.DEFAULT_NVIDIA_DOCKER_PLUGIN_ENDPOINT);
    if (null == endpoint || endpoint.isEmpty()) {
      LOG.info(YarnConfiguration.NVIDIA_DOCKER_PLUGIN_ENDPOINT
          + " set to empty, skip init ..");
      return;
    } else {
      String cliOptions;
      try {
        // Talk to plugin server and get options
        URL url = new URL(endpoint);
        URLConnection uc = url.openConnection();
        uc.setRequestProperty("X-Requested-With", "Curl");

        StringWriter writer = new StringWriter();
        IOUtils.copy(uc.getInputStream(), writer, "utf-8");
        cliOptions = writer.toString();

        LOG.info("Additional docker CLI options from plugin to run GPU "
            + "containers:" + cliOptions);

        // Parse cli options
        // Examples like:
        // --device=/dev/nvidiactl --device=/dev/nvidia-uvm --device=/dev/nvidia0
        // --volume-driver=nvidia-docker
        // --volume=nvidia_driver_352.68:/usr/local/nvidia:ro

        for (String str : cliOptions.split(" ")) {
          str = str.trim();
          if (str.startsWith(DEVICE_OPTION)) {
            addToCommand(DEVICE_OPTION, getValue(str));
          } else if (str.startsWith(VOLUME_DRIVER_OPTION)) {
            addToCommand(VOLUME_DRIVER_OPTION, getValue(str));
          } else if (str.startsWith(MOUNT_RO_OPTION)) {
            String mount = getValue(str);
            if (!mount.endsWith(":ro")) {
              throw new IllegalArgumentException(
                  "Should not have mount other than ro, command=" + str);
            }
            addToCommand(MOUNT_RO_OPTION,
                mount.substring(0, mount.lastIndexOf(':')));
          } else {
            throw new IllegalArgumentException("Failed to ")
          }
        }

      } catch (Exception e) {
        LOG.warn("Exception of " + this.getClass().getSimpleName() + " init:",
            e);
        throw new ContainerExecutionException(e);
      }
    }
  }

  // TODO, add a container id to get if it has any GPU assigned.
  @Override
  public synchronized void updateDockerRunCommand(DockerRunCommand dockerRunCommand) {

  }
}
