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

package org.apache.hadoop.yarn.submarine.client.common;

import org.apache.hadoop.yarn.api.records.Resource;

import java.util.ArrayList;
import java.util.List;

/**
 * Parameters used to run a job
 */
public class RunJobParameters {
  private String name;
  private String input;
  private String jobDir;

  private String savedModelPath;
  private int numWorkers;
  private int numPS;
  private Resource workerResource;
  private Resource psResource;
  private String queue;
  private boolean tensorboardEnabled;
  private String workerLaunchCmd;
  private String psLaunchCmd;
  private String dockerImageName;
  private boolean verbose;

  private List<String> envars = new ArrayList<>();

  public String getDockerImageName() {
    return dockerImageName;
  }

  public RunJobParameters setDockerImageName(String dockerImageName) {
    this.dockerImageName = dockerImageName;
    return this;
  }


  public String getJobName() {
    return name;
  }

  public RunJobParameters setJobName(String name) {
    this.name = name;
    return this;
  }

  public String getInput() {
    return input;
  }

  public RunJobParameters setInput(String input) {
    this.input = input;
    return this;
  }

  public String getJobDir() {
    return jobDir;
  }

  public RunJobParameters setJobDir(String jobDir) {
    this.jobDir = jobDir;
    return this;
  }

  public String getSavedModelPath() {
    return savedModelPath;
  }

  public RunJobParameters setSavedModelPath(String savedModelPath) {
    this.savedModelPath = savedModelPath;
    return this;
  }

  public int getNumWorkers() {
    return numWorkers;
  }

  public RunJobParameters setNumWorkers(int numWorkers) {
    this.numWorkers = numWorkers;
    return this;
  }

  public int getNumPS() {
    return numPS;
  }

  public RunJobParameters setNumPS(int numPS) {
    this.numPS = numPS;
    return this;
  }

  public Resource getWorkerResource() {
    return workerResource;
  }

  public RunJobParameters setWorkerResource(Resource workerResource) {
    this.workerResource = workerResource;
    return this;
  }

  public Resource getPsResource() {
    return psResource;
  }

  public RunJobParameters setPsResource(Resource psResource) {
    this.psResource = psResource;
    return this;
  }

  public String getQueue() {
    return queue;
  }

  public RunJobParameters setQueue(String queue) {
    this.queue = queue;
    return this;
  }

  public boolean isTensorboardEnabled() {
    return tensorboardEnabled;
  }

  public RunJobParameters setTensorboardEnabled(boolean tensorboardEnabled) {
    this.tensorboardEnabled = tensorboardEnabled;
    return this;
  }

  public String getWorkerLaunchCmd() {
    return workerLaunchCmd;
  }

  public RunJobParameters setWorkerLaunchCmd(String workerLaunchCmd) {
    this.workerLaunchCmd = workerLaunchCmd;
    return this;
  }

  public String getPSLaunchCmd() {
    return psLaunchCmd;
  }

  public RunJobParameters setPSLaunchCmd(String psLaunchCmd) {
    this.psLaunchCmd = psLaunchCmd;
    return this;
  }

  public List<String> getEnvars() {
    return envars;
  }

  public RunJobParameters setEnvars(List<String> envars) {
    this.envars = envars;
    return this;
  }


  public boolean isVerbose() {
    return verbose;
  }

  public RunJobParameters setVerbose(boolean verbose) {
    this.verbose = verbose;
    return this;
  }

}
