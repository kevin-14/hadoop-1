package org.apache.hadoop.yarn.applications.yalp.client;

import org.apache.hadoop.yarn.api.records.Resource;

/**
 * Parameters used to run a job
 */
public class RunJobParameters {
  private String name;
  private String input;
  private String output;
  private int numWorkers;
  private int numPS;
  private Resource workerResource;
  private Resource psResource;
  private String queue;
  private boolean tensorboardEnabled;
  private String[] workerLaunchCmd;
  private String dockerImageName;

  public String getDockerImageName() {
    return dockerImageName;
  }

  public RunJobParameters setDockerImageName(String dockerImageName) {
    this.dockerImageName = dockerImageName;
    return this;
  }


  public String getName() {
    return name;
  }

  public RunJobParameters setName(String name) {
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

  public String getOutput() {
    return output;
  }

  public RunJobParameters setOutput(String output) {
    this.output = output;
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

  public String[] getWorkerLaunchCmd() {
    return workerLaunchCmd;
  }

  public RunJobParameters setWorkerLaunchCmd(String[] workerLaunchCmd) {
    this.workerLaunchCmd = workerLaunchCmd;
    return this;
  }
}
