package org.apache.hadoop.yarn.applications.yalp.client.cli;

import org.apache.commons.cli.ParseException;
import org.apache.hadoop.yarn.exceptions.YarnException;

import java.io.IOException;

public abstract class AbstractCli {
  protected ClientContext cliContext;

  public AbstractCli(ClientContext cliContext) {
    this.cliContext = cliContext;
  }

  public abstract void run(String[] args)
      throws ParseException, IOException, YarnException;
}
