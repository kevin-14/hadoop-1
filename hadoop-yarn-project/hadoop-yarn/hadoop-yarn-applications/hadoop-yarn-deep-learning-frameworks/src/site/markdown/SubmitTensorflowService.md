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

# How to use custom scripts to run Tensorflow on YARN with ease

## Prerequisites
1) User need to ensure that script `submit_tf_job.py` is placed with other template files such as `presetup_tf.sh_template` and `example_tf_job_spec.json` in same folder.
2) Sufficient permissions (authorization and authentication) are needed for the user (in case of secure cluster) to run this script as it could also support submitting Tensorflow service directly to YARN.

## Setup presetup_tf.sh template
1) Rename `presetup_tf.sh_template` to `presetup_tf.sh`
2) In `presetup_tf.sh`, update valid `HADOOP_HDFS_HOME` value as per cluster configuration.
3) Also update `JAVA_HOME` as per the environment setup.
4) Place `presetup-tf.sh` in HDFS under `hdfs://default/tf-job-conf/scripts`.
5) Ensure that `remote_conf_path` is created and available in the docker container. Config files will be copied from HDFS to this location for each container.

## Run submit_tf_job.py to submit Tensorflow job to YARN

User could run below command to submit Tensorflow job to YARN or to generate valid Yarnfile for the job.
`python submit_tf_job.py --remote_conf_path <REMOTE_CONF_PATH> --input_spec <INPUT_SPEC> --docker_image <DOCKER_IMAGE> --env <ENV> --job_name <JOB_NAME> --user <USER> --domain <DOMAIN> --submit --distributed --kerberos`

Detailed argument summary for `submit_tf_job.py` command.

```
mandatory arguments:
  -remote_conf_path    Remote Configuration path to run TF job
  -input_spec          Yarnfile specification template for TF job

optional arguments:
  --docker_image        Docker image name for TF job.
  --env                 Environment variables needed for TF job in key=value
                        format.
  --submit              Automatically submit TF job to YARN, if this is not
                        specified. New generated spec will be printed to
                        <stdout>, and user can use yarn app -launch
                        <path/to/spec> to launch it later.
  --job_name            Specify job name of the Tensorflow job, which will
                        overwrite the one specified in input spec file
  --user                Specify user name if it is different from $USER (e.g.
                        kinit user)
  --domain              Cluster domain name, which should be same as
                        hadoop.registry.dns.domain-name in yarn-site.xml,
                        required for distributed Tensorflow
  --distributed         Running distributed tensorflow, if this is specified,
                        worker/ps/master must be included inside input spec
  --kerberos            Is this a kerberos-enabled cluster or not
  --verbose             Print debug information
  -h, --help            show this help message and exit
```

Example:
`python src/main/tensorflow/scripts/submit_tf_job.py --remote_conf_path='/remote/conf' --input_spec=src/main/tensorflow/scripts/example_tf_job_spec.json --job_name=job_123 --domain example.com --distributed --verbose --docker_image=ubuntu16:04`

`--submit` will be help user to auto-submit TF job to YARN given this command is ran from a Hadoop box.
 
## General Guidelines

1) For many env configurations, extra escape characters are used so that native service could export correct env's as per guideline. An improvement to this is ongoing in YARN-8257.
2) User could use an existing template and edit the necessary sections as per the requirement.
3) In secure clusters, user need to ensure that app is launched from a secure shell.