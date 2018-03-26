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

package org.apache.hadoop.yarn.applications.yalp.client.cli;

import org.apache.hadoop.yarn.applications.yalp.client.common.Constants;
import org.apache.hadoop.yarn.applications.yalp.client.common.MockClientContext;
import org.apache.hadoop.yarn.service.api.records.Component;
import org.apache.hadoop.yarn.service.api.records.Service;
import org.junit.Assert;
import org.junit.Test;

public class TestRunJobCli {
  @Test
  public void testPrintHelp() {
    MockClientContext mockClientContext = new MockClientContext();
    RunJobCli runJobCli = new RunJobCli(mockClientContext);
    runJobCli.printUsages();
  }

  @Test
  public void testBasicRunJob()
      throws Exception {
    MockClientContext mockClientContext = new MockClientContext();
    RunJobCli runJobCli = new RunJobCli(mockClientContext);
    runJobCli.run(
        new String[] { "--name", "my-job", "--docker_image", "tf-docker:1.1.0",
            "--input", "hdfs://input", "--output", "hdfs://output",
            "--num_workers", "3", "--num_ps", "2", "--worker_launch_cmd",
            "python run-job.py", "--worker_resources", "memory=2048,vcores=2",
            "--ps_resources", "memory=4096,vcores=4", "--tensorboard", "true",
            "--ps_launch_cmd", "python run-ps.py" });
    Service serviceSpec = runJobCli.getServiceSpec();
    Assert.assertEquals(2, serviceSpec.getComponents().size());
    Assert.assertTrue(
        serviceSpec.getComponent(Constants.WORKER_COMPONENT_NAME) != null);
    Assert.assertTrue(
        serviceSpec.getComponent(Constants.PS_COMPONENT_NAME) != null);
    Component workerComp = serviceSpec.getComponent(
        Constants.WORKER_COMPONENT_NAME);
    Assert.assertEquals(2048, workerComp.getResource().calcMemoryMB());
    Assert.assertEquals(2, workerComp.getResource().getCpus().intValue());

    Component psComp = serviceSpec.getComponent(
        Constants.PS_COMPONENT_NAME);
    Assert.assertEquals(4096, psComp.getResource().calcMemoryMB());
    Assert.assertEquals(4, psComp.getResource().getCpus().intValue());

    // TODO, ADD TEST TO USE SERVICE CLIENT TO VALIDATE THE JSON SPEC
  }
}
