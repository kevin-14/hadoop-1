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

# Run Tensorflow Jobs Using Helper Script

## Prerequisites
1) User need to ensure that script `submit_tf_job.py` is placed with other template files such as `presetup_tf.sh_template` and `example_tf_job_spec.json` in same folder.
~~ This not required, correct?
2) Sufficient permissions (authorization and authentication, like `kinit`) are needed for the user (in case of secure cluster) to run this script as it could also support submitting Tensorflow service directly to YARN.

## Setup presetup_tf.sh template
1) Rename `presetup_tf.sh_template` to `presetup_tf.sh`

2) In `presetup_tf.sh`:
- Update valid `HADOOP_HDFS_HOME` value as per cluster configuration.
- Update `JAVA_HOME` as per the environment setup. This should point to `JAVA_HOME` **inside the docker image**.

4) Place `presetup-tf.sh` in HDFS under `hdfs://host:port/<tf-job-conf-path>/scripts`.
~~ I would prefer to remove the `scripts`, this is just like yarn-env.sh, we can directly place it under /etc/hadoop/conf instead of /etc/hadoop/conf/scripts. This requries changes to python file.

5) Ensure that `<tf-job-conf-path>` is created and available in the docker container. Config files will be copied from HDFS to this location for each container.
~~ Why this is required in the docker container? I think YARN will localize it and mount to docker docker. 
~~ I would suggest to just state that, <tf-job-conf-path> in HDFS folder should be accessible by the submit user.

6) Upload core-site.xml, hdfs-site.xml, and (when security is enabled) krb5.conf to `<tf-job-conf-path>`.

## Run submit_tf_job.py to submit Tensorflow job to YARN

User could run below command to submit Tensorflow job to YARN or to generate valid Yarnfile for the job.
`python submit_tf_job.py --remote_conf_path <tf-job-conf-path> --input_spec <INPUT_SPEC> --docker_image <DOCKER_IMAGE> --env <ENV> --job_name <JOB_NAME> --user <USER> --domain <DOMAIN> --submit --distributed --kerberos`

Detailed argument summary for `submit_tf_job.py` command.

```
mandatory arguments:
  -remote_conf_path    Remote Configuration path to run TF job, this is a HDFS path.
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
`python src/main/tensorflow/scripts/submit_tf_job.py --remote_conf_path='hdfs://host:port/<tf-job-conf-path>' --input_spec=src/main/tensorflow/scripts/example_tf_job_spec.json --job_name=job_123 --domain example.com --distributed --verbose --docker_image=ubuntu16:04`

`--submit` will be help user to auto-submit TF job to YARN given this command is ran from a Hadoop box.

## Provide `input-spec` file to run Tensorflow jobs

### Run standalone TF job. 

```

{
  "name": "standalone-tf",
  "version": "1.0.0",
  "components": [
    {
      "name": "worker",
      "dependencies": [],
      "resource": {
        "cpus": 1,
        "memory": "4096",
        "additional" : {
          "yarn.io/gpu" : {
            "value" : 1
          }
        }
      },
      "launch_command": "python cifar10_main.py --data-dir=hdfs://default/tmp/cifar-10-data --job-dir=hdfs://default/tmp/cifar-10-jobdir --train-steps=10000 --num-gpus=1 --eval-batch-size=16 --train-batch-size=16 --sync",
      "number_of_containers": 1
    }
  ],
  "configuration": {
    "properties": {},
    "env": {
    }
  },
  "kerberos_principal" : {
    "principal_name" : "ambari-qa@EXAMPLE.COM",
    "keytab" : "file:///etc/security/keytabs/smokeuser.headless.keytab"
  }
}

```

Notes:
- `hdfs://host:port/<tf-job-conf-path>/presetup-tf.sh` will be automatically download and mount to the docker container, it will be executed before invoking `launch_command` of components specified in the spec.
- Component name can be customized (In above example it uses `worker`)
- In `resource` section, you can specify cpu/memory/gpu if you needed.
- Additional environment variables can be specified under `env`. This will be passed to launched docker container process.
- 

### Run distributed TF job.

```
{
  "name": "distributed-tf",
  "version": "1.0.0",
  "components": [
    {
      "name": "master",
      "dependencies": [],
      "resource": {
        "cpus": 1,
        "memory": "4096",
        "additional" : {
          "yarn.io/gpu" : {
            "value" : 1
          }
        }
      },
      "launch_command": "cd /test/models/tutorials/image/cifar10_estimator && python cifar10_main.py --data-dir=hdfs://default/tmp/cifar-10-data --job-dir=hdfs://default/tmp/cifar-10-jobdir --train-steps=10000 --num-gpus=1 --eval-batch-size=16 --train-batch-size=16 --sync",
      "number_of_containers": 1
    },
    {
      "name": "worker",
      "dependencies": [],
      "resource": {
        "cpus": 1,
        "memory": "4096",
        "additional" : {
          "yarn.io/gpu" : {
            "value" : 1
          }
        }
      },
      "launch_command": "cd /test/models/tutorials/image/cifar10_estimator && python cifar10_main.py --data-dir=hdfs://default/tmp/cifar-10-data --job-dir=hdfs://default/tmp/cifar-10-jobdir --train-steps=10000 --num-gpus=1 --eval-batch-size=16 --train-batch-size=16 --sync",
      "number_of_containers": 1
    },
    {
      "name": "ps",
      "dependencies": [],
      "resource": {
        "cpus": 1,
        "memory": "2048",
        "additional" : {
          "yarn.io/gpu" : {
            "value" : 1
          }
        }
      },
      "launch_command": "cd /test/models/tutorials/image/cifar10_estimator && python cifar10_main.py --data-dir=hdfs://default/tmp/cifar-10-data --job-dir=hdfs://default/tmp/cifar-10-jobdir --num-gpus=1",
      "number_of_containers": 1
    }
  ],
  "configuration": {
    "properties": {},
    "env": {
    }
  },
  "kerberos_principal" : {
    "principal_name" : "ambari-qa@EXAMPLE.COM",
    "keytab" : "file:///etc/security/keytabs/smokeuser.headless.keytab"
  }
}
```

Notes: (In addition to standalone TF spec)
- For distributed Tensorflow launch spec, `master`, `worker`, `ps` components are mandatory.
- Different value of `num_of_containers` can be specified for `worker` and `ps`.
- `TF_CONFIG` will be automatically generated and insert to spec to launch the job to do distributed training, you don't need to worry about this. 

### Addition information about writing Yarnfile

#### Security

YARN Native Services provide an easy way to run services in a secure cluster by providing keytabs and kerberos principal as simple json input parameters.
```
"kerberos_principal" : {
      "principal_name" : <your-principle-name>,
      "keytab" : <path-to-your-keytab>
}
```
User could define the kerberos principal name (eg: test-user@EXAMPLE.COM) and keytab file path from HDFS or local file system. Given these information, user could run service from a secured shell.

#### Choosing correct Docker images for the application
```
"artifact" : {
  "id" : <TODO: docker image name>,
  "type" : "DOCKER"
}
```
Under each service component, user need to provide docker image name so that native service will use this image to launch containers.
User can use `--docker_image` to overwrite whatever defined in the input job spec.
 
## General Guidelines

1) For many env configurations, extra escape characters are used so that native service could export correct env's as per guideline. An improvement to this is ongoing in YARN-8257. 
2) In secure clusters, user need to ensure that app is launched from a secure shell. (e.g. with proper Kerberos token inited).

## End-to-end example: 

### Run Cifar10 distributed Tensorflow training on GPU/security-enabled cluster

~~ Do this after we validate example and fix further issues of submit-tf-job.py script
~~ <TODO>: Add an end-to-end example to make sure user can play with. Which can simply include:
~~ launch command line.
~~ Dockerfile (path to local file)
~~ Input-YARN-file (path to local file)
~~ (We may not need other examples since user can easily update to use non-GPU, non-security, single node, etc.

