/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.yarn.submarine.client.cli;

import org.apache.hadoop.yarn.submarine.client.common.Constants;
import org.apache.hadoop.yarn.submarine.client.common.MockClientContext;
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
  public void testBasicRunJobForDistributedTraining() throws Exception {
    MockClientContext mockClientContext = new MockClientContext();
    RunJobCli runJobCli = new RunJobCli(mockClientContext);
    Assert.assertFalse(mockClientContext.isVerbose());

    runJobCli.run(
        new String[] { "--name", "my-job", "--docker_image", "tf-docker:1.1.0",
            "--input", "hdfs://input", "--job_dir", "hdfs://output",
            "--num_workers", "3", "--num_ps", "2", "--worker_launch_cmd",
            "python run-job.py", "--worker_resources", "memory=2048,vcores=2",
            "--ps_resources", "memory=4096,vcores=4", "--tensorboard", "true",
            "--ps_launch_cmd", "python run-ps.py", "--verbose" });
    Service serviceSpec = runJobCli.getServiceSpec();
    Assert.assertEquals(3, serviceSpec.getComponents().size());
    Assert.assertTrue(
        serviceSpec.getComponent(Constants.WORKER_COMPONENT_NAME) != null);
    Assert.assertTrue(
        serviceSpec.getComponent(Constants.PRIMARY_WORKER_COMPONENT_NAME)
            != null);
    Assert.assertTrue(
        serviceSpec.getComponent(Constants.PS_COMPONENT_NAME) != null);
    Component primaryWorkerComp = serviceSpec.getComponent(
        Constants.PRIMARY_WORKER_COMPONENT_NAME);
    Assert.assertEquals(2048, primaryWorkerComp.getResource().calcMemoryMB());
    Assert.assertEquals(2,
        primaryWorkerComp.getResource().getCpus().intValue());

    Component workerComp = serviceSpec.getComponent(
        Constants.WORKER_COMPONENT_NAME);
    Assert.assertEquals(2048, workerComp.getResource().calcMemoryMB());
    Assert.assertEquals(2, workerComp.getResource().getCpus().intValue());

    Component psComp = serviceSpec.getComponent(Constants.PS_COMPONENT_NAME);
    Assert.assertEquals(4096, psComp.getResource().calcMemoryMB());
    Assert.assertEquals(4, psComp.getResource().getCpus().intValue());

    Assert.assertTrue(mockClientContext.isVerbose());

    // TODO, ADD TEST TO USE SERVICE CLIENT TO VALIDATE THE JSON SPEC
  }

  @Test
  public void testBasicRunJobForSingleNodeTraining() throws Exception {
    MockClientContext mockClientContext = new MockClientContext();
    RunJobCli runJobCli = new RunJobCli(mockClientContext);
    Assert.assertFalse(mockClientContext.isVerbose());

    runJobCli.run(
        new String[] { "--name", "my-job", "--docker_image", "tf-docker:1.1.0",
            "--input", "hdfs://input", "--job_dir", "hdfs://output",
            "--num_workers", "1", "--worker_launch_cmd", "python run-job.py",
            "--worker_resources", "memory=2048,vcores=2", "--tensorboard",
            "true", "--verbose" });
    Service serviceSpec = runJobCli.getServiceSpec();
    Assert.assertEquals(1, serviceSpec.getComponents().size());
    Assert.assertTrue(
        serviceSpec.getComponent(Constants.PRIMARY_WORKER_COMPONENT_NAME)
            != null);
    Component primaryWorkerComp = serviceSpec.getComponent(
        Constants.PRIMARY_WORKER_COMPONENT_NAME);
    Assert.assertEquals(2048, primaryWorkerComp.getResource().calcMemoryMB());
    Assert.assertEquals(2,
        primaryWorkerComp.getResource().getCpus().intValue());

    Assert.assertTrue(mockClientContext.isVerbose());

    // TODO, ADD TEST TO USE SERVICE CLIENT TO VALIDATE THE JSON SPEC
  }

  @Test
  public void testLaunchCommandPatternReplace() throws Exception {
    MockClientContext mockClientContext = new MockClientContext();
    RunJobCli runJobCli = new RunJobCli(mockClientContext);
    Assert.assertFalse(mockClientContext.isVerbose());

    runJobCli.run(
        new String[] { "--name", "my-job", "--docker_image", "tf-docker:1.1.0",
            "--input", "hdfs://input", "--job_dir", "hdfs://output",
            "--num_workers", "3", "--num_ps", "2", "--worker_launch_cmd",
            "python run-job.py --input=%input% --model_dir=%job_dir% --export_dir=%savedmodel_path%/savedmodel",
            "--worker_resources", "memory=2048,vcores=2", "--ps_resources",
            "memory=4096,vcores=4", "--tensorboard", "true", "--ps_launch_cmd",
            "python run-ps.py --input=%input% --model_dir=%job_dir%/model",
            "--verbose" });
    Service serviceSpec = runJobCli.getServiceSpec();
    Assert.assertEquals(3, serviceSpec.getComponents().size());
    Assert.assertTrue(
        serviceSpec.getComponent(Constants.WORKER_COMPONENT_NAME) != null);
    Assert.assertTrue(
        serviceSpec.getComponent(Constants.PRIMARY_WORKER_COMPONENT_NAME)
            != null);
    Assert.assertTrue(
        serviceSpec.getComponent(Constants.PS_COMPONENT_NAME) != null);
    Component primaryWorkerComp = serviceSpec.getComponent(
        Constants.PRIMARY_WORKER_COMPONENT_NAME);
    Assert.assertEquals(2048, primaryWorkerComp.getResource().calcMemoryMB());
    Assert.assertEquals(2,
        primaryWorkerComp.getResource().getCpus().intValue());

    Component workerComp = serviceSpec.getComponent(
        Constants.WORKER_COMPONENT_NAME);
    Assert.assertEquals(2048, workerComp.getResource().calcMemoryMB());
    Assert.assertEquals(2, workerComp.getResource().getCpus().intValue());

    Component psComp = serviceSpec.getComponent(Constants.PS_COMPONENT_NAME);
    Assert.assertEquals(4096, psComp.getResource().calcMemoryMB());
    Assert.assertEquals(4, psComp.getResource().getCpus().intValue());

    Assert.assertTrue(mockClientContext.isVerbose());

    Assert.assertEquals(
        "python run-job.py --input=hdfs://input --model_dir=hdfs://output --export_dir=hdfs://output/savedmodel",
        runJobCli.getRunJobParameters().getWorkerLaunchCmd());
    Assert.assertEquals(
        "python run-ps.py --input=hdfs://input --model_dir=hdfs://output/model",
        runJobCli.getRunJobParameters().getPSLaunchCmd());

    // TODO, ADD TEST TO USE SERVICE CLIENT TO VALIDATE THE JSON SPEC
  }
}
