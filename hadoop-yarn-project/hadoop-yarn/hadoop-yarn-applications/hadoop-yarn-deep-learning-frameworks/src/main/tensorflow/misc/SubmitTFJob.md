<!--
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
-->

# How to submit a Tensorflow job on YARN

## Prerequisites
1. Create a folder named `tf-job-conf` with valid permissions under HDFS folder structure. For this example, assuming `hdfs://default/tf-job-conf` as valid path.
2. `/scripts` and `/configs` folders has to be present in docker container to mount configs and scripts.
3. A yarnfile template json spec has to be kept to provide as input. Refer `yarnfile/examples/distributed-tf-gpu-sample.yarnfile`

## Setting up presetup-tf.sh
1. Use `presetup-tf.sh_template` to create a valid `presetup-tf.sh` based on correct `HADOOP_HDFS_HOME`.
2. Place `presetup-tf.sh` in HDFS under `hdfs://default/tf-job-conf/scripts`.

## Using `submit_tf_job.py` for easier job submission
Usage:
```
usage: submit_tf_job.py [-h] -remote_conf_path REMOTE_CONF_PATH -input_spec
                        INPUT_SPEC -image_name IMAGE_NAME -env ENV
                        -tf_config_params TF_CONFIG_PARAMS [--submit]

Submit Tensorflow job to YARN.

optional arguments:
  -h, --help            show this help message and exit
  -remote_conf_path REMOTE_CONF_PATH
                        Remote Configuration path to run TF job
  -input_spec INPUT_SPEC
                        Yarnfile specification for TF job.
  -image_name IMAGE_NAME
                        Docker image name for TF job.
  -env ENV              Environment variables needed for TF job in key=value
                        format.
  -tf_config_params TF_CONFIG_PARAMS
                        Input params needed for TF_CONFIG env in
                        <username>:<domain_name>:<service_name>:<num_workers
                        (exclude master)>:<num_ps> format.
  --submit              Automatically submit TF job to YARN.
 ```
 
 Example:
 ```
 python submit_tf_job.py -remote_conf_path hdfs://default/tf-job-conf -input_spec /tmp/tf.json -tf_config_params ambari-qa:test.com:distributed_tf:1:1 -image_name docker.io/tf-on-yarn-example:1.3.0 -env JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64/jre/:HADOOP_CONF_DIR=/etc/hadoop/conf:YARN_CONTAINER_RUNTIME_DOCKER_CONTAINER_NETWORK=hadoop
 ```
 
 If user does not opt to provide `--submit` option, this script will generate json spec which could be used to submit as a YARN service job.