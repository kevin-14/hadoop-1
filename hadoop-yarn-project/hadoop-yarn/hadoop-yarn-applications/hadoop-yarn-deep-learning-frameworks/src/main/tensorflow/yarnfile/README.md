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

# How to write Yarnfile to run Tensorflow on YARN

## Identifying key parameters specific to Tensorflow application

### 1) Defining resource needed for each component from YARN
```
"resource": {
    "cpus": 1,
    "memory": "4096",
    "additional" : {
        "yarn.io/gpu" : {
           "value" : 2
        }
    }
}
```

Under each service component (for eg: worker or ps etc), user can define the resources which are needed to run this component.
`cpus` and `memory` are legacy resources supported by YARN. In addition to this, `yarn.io/gpu` could also be defined as additional resources as needed. User need to provide the number of GPU's needed for this component in `yarn.io/gpu` section.

After configuring GPU's in resource section, user can provide `--num-gpus=2` in align to same number of GPU's asked. These two configurations are needed to use GPU for each Tensorflow component. 

Note: For this example, 2 GPU resources are used for reference. User could choose any number of devices as required for the Tensorflow workload based on GPU resources available in cluster.

### 2) Using HDFS as storage layer

```
 "launch_command": "export HADOOP_HDFS_HOME=<TODO: $HDFS_HOME>;
```
Each service component section must specify launch_command. To use HDFS as storage layer, user need to configure `HDFS_HOME` directory path in above part of the service component definition.

```
"env": {
    "HADOOP_CONF_DIR" : <TODO: $HADOOP_CONF_DIR of the docker container>,
    "JAVA_HOME" : <TODO: $JAVA_HOME of the docker container>,
    ...
}
```

YARN native service specification helps to define environment variables for all components as common. User need to configure path for `HADOOP_CONF_DIR` in above section.

Post these changes, user could use HDFS path instead of local path for various Tensorflow config parameters specific to each application.
For eg: cifar_10 launch_command could use HDFS as follows
```
python cifar10_main.py --data-dir=hdfs://default/tmp/cifar-10-data \
--job-dir=hdfs://default/tmp/cifar-10-jobdir --train-steps=10000 --num-gpus=2
```

### 3) Running Tensorflow apps in a secure YARN cluster

YARN Native Services provide an easy way to run services in a secure cluster by providing keytabs and kerberos principal as simple json input parameters.
```
"kerberos_principal" : {
      "principal_name" : <your-principle-name>,
      "keytab" : <path-to-your-keytab>
}
```
User could define the kerberos principal name (eg: test-user@EXAMPLE.COM) and keytab file path from HDFS or local file system. Given these information, user could run service from a secured shell.

### 4) Defining custom environment variables

```
"env": {
    "TF_CONFIG" : <TODO: insert generated $TF_CONFIG>,
    "HADOOP_CONF_DIR" : <TODO: $HADOOP_CONF_DIR of the docker container>,
    "JAVA_HOME" : <TODO: $JAVA_HOME of the docker container>,
    "YARN_CONTAINER_RUNTIME_DOCKER_CONTAINER_NETWORK": "bridge (Or other overlay network)"
}
```

a) `TF_CONFIG`: Use `$HADOOP/hadoop-yarn-project/hadoop-yarn/hadoop-yarn-applications/hadoop-yarn-deep-learning-frameworks/src/main/tensorflow/misc/generate_tf_config.py` script to generate TF_CONFIG env variable.

Usage: `python generate_tf_config.py <user-name> <domain-name> <service-name> <num-workers> <num-ps>`

b) `JAVA_HOME`: User could configure the java home patch as a common env variable.

c) `YARN_CONTAINER_RUNTIME_DOCKER_CONTAINER_NETWORK`: Based on the cluster network topology, user could choose network as `bridge` or other overlay networks as per the cluster configurations. (for eg: bridge, hadoop, host)

### 5) Choosing correct Docker images for the application
```
"artifact" : {
  "id" : <TODO: docker image name>,
  "type" : "DOCKER"
}
```
Under each service component, user need to provide docker image name so that native service will use this image to launch containers.

User also need to ensure that these images are available in respective docker repo configured for this YARN cluster.
            

## General Guidelines

1) For many env configurations, extra escape characters are used so that native service could export correct env's as per guideline. An improvement to this is ongoing in YARN-8257.
2) User could use an existing template and edit the necessary sections as per the requirement.
3) In secure clusters, user need to ensure that app is launched from a secure shell.