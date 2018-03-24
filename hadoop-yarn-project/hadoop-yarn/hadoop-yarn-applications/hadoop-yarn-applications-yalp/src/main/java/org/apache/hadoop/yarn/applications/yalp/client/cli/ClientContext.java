package org.apache.hadoop.yarn.applications.yalp.client.cli;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.conf.YarnConfiguration;

public class ClientContext {
  private Configuration conf = new YarnConfiguration();

  public YarnClient getOrCreateYarnClient() {
    return null;
  }

  public Configuration getConfiguration() {
    return conf;
  }

  public void setConfiguration(Configuration conf) {
    this.conf = conf;
  }
}
