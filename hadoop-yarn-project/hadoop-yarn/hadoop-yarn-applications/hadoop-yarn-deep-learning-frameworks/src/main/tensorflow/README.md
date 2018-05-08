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
# Running Tensorflow on YARN

## Prepare environment

(Following steps need to be done on all your NMs)

1) Checkout https://github.com/tensorflow/models/:
```
git clone https://github.com/tensorflow/models/
```

2) Go to `models/tutorials/image/cifar10_estimator`

3) Generate data by using following command: (required Tensorflow installed) 

```
python generate_cifar10_tfrecords.py --data-dir=/tmp/cifar-10-data
```

4) Upload data to HDFS

```
hadoop fs -put /tmp/cifar-10-data/ /tmp/
```

## Use Yarnfile and run job 

Yarnfile is a normal JSON file, typically you should save the Yarnfile to a local file and use following command to run:

```
yarn app -launch distributed-tf <path-to-saved-yarnfile>
```

Or you can use curl to post the yarnfile.

```
hadoop fs -rmr /tmp/cifar-10-jobdir;
yarn application -destroy distributed-tf;
curl --negotiate -u: -H "Content-Type: application/json" \
  -X POST http://<RM-host>:8088/app/v1/services -d '... content of Yarnfile...'
```

Please note that:

a. All following examples are using ```/tmp/cifar-10-jobdir``` as snapshot directory for training, so suggest to run:
```
hadoop fs -rmr /tmp/cifar-10-jobdir
```
To cleanup snapshot between runs. 

b. YARN service doesn't allow multiple services with the same name, so please run following command
```
yarn application -destroy <service-name> 

```
To delete services if you want to reuse the same service name.

## Example Yarnfiles

### Generate Dockerfiles

Please refer to ```dockerfile/README.md``` for more details.

### Single node Tensorflow (with GPU and access Kerberorized HDFS)

```
{
    "name": "single-node-tensorflow",
    "version": "1.0.0",
    "components": [
        {
            "artifact" : {
              "id" : <docker-image-name>,
              "type" : "DOCKER"
            },
            "name": "worker",
            "dependencies": [],
            "resource": {
                "cpus": 1,
                "memory": "4096",
                "additional" : {
                  "yarn.io/gpu" : {
                    "value" : 2
                   }
                }
            },
            "launch_command": "export HADOOP_HDFS_HOME=/hadoop-3.1.0; export HADOOP_HOME=; export HADOOP_YARN_HOME=; export HADOOP_CONF_DIR=/etc/hadoop/conf; export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64/jre/; export CLASSPATH=\\`\\$HADOOP_HDFS_HOME/bin/hadoop classpath --glob\\`; export LD_LIBRARY_PATH=\\$LD_LIBRARY_PATH:/usr/lib/jvm/java-8-openjdk-amd64/jre/lib/amd64/server/; cd /test/models/tutorials/image/cifar10_estimator  && ls -l && python cifar10_main.py --data-dir=hdfs://default/tmp/cifar-10-data --job-dir=hdfs://default/tmp/cifar-10-jobdir --num-gpus=1 --train-batch-size=16 --train-steps=40000",
            "number_of_containers": 1,
            "run_privileged_container": false
        }
    ],
    "kerberos_principal" : {
      "principal_name" : "ambari-qa@EXAMPLE.COM",
      "keytab" : "file:///etc/security/keytabs/smokeuser.headless.keytab"
    }
}
```

### Distributed Tensorflow (with CPU and access non-Kerberorized HDFS)

#### Generate TF_CONFIG

```TF_CONFIG``` is an environment variable which passes training parameters to Tensorflow. It is widely used to run distributed Tensorflow training.
 
Here's an example of ```TF_CONFIG```
 
```
{  
   "cluster":{  
      "master":[  
         "<master-host>:<port>"
      ],
      "ps":[  
         "<ps-host-0>:<port>",
         "<ps-host-1>:<port>",
         "<ps-host-2>:<port>"
         ...
      ],
      "worker":[  
         "<worker-host-0>:<port>",
         "<worker-host-1>:<port>",
         "<worker-host-2>:<port>"
         ...
      ]
   },
   "task":{  
      "type": "worker",
      "index": 0
   },
   "environment":"cloud"
}
```

It includes two parts, the first is ```cluster```. ```cluster``` is a collection of endpoints of all roles of a Tensorflow job. Roles include:
- ```ps```: saves the parameters among all workers. All workers can read/write/update the parameters for model via ps. As some models are extremely large the parameters are shared among the ps (each ps stores a subset).
- ```worker```: does the training.
- ```master```: basically a special worker, it does training, but also restores and saves checkpoints and do evaluation.
```cluster``` part is identical to all roles of a given Tensorflow job. 

(Description of these roles copied from https://github.com/tensorflow/models/tree/master/tutorials/image/cifar10_estimator)

The second is ```task```, which describes role of the launched process, which is different for different roles. For example, if ```task``` is specified to: 
```
   "task":{  
      "type": "worker",
      "index": 0
   }
```
The launched instance will use: ```<worker-host-0>:<port>``` as endpoint.

Following script can be used to generate ```TF_CONFIG```:

```
import sys
def get_component_array(name, count, hostname_suffix):
    component = "\\\\" +  '\\"' + name + "\\\\" + '\\":'
    component_names = '['
    for i in xrange(0, count):
        component_names = component_names + "\\\\" + '\\' + '"' + name + "-" + str(i) + hostname_suffix + "\\\\" + '\\"'
        if i != count - 1:
            component_names = component_names + ','
    component_names = component_names + ']'
    return component + component_names
def get_key_value_pair(name, keys, values, count):
    block_name = "\\\\" +  '\\"' + name + "\\\\" + '\\":'
    block_values = '{'
    for i in xrange(0, count):
        if count == 1:
            block_values = block_values + "\\\\" + '\\' + '"' + values[i] + "\\\\" + '\\"'
            break
        block_values = block_values + "\\\\" + '\\' + '"' + keys[i] + "\\\\" + '\\"' + ':' + "\\\\" + '\\"' + values[i] + "\\\\" + '\\"'
        if i != count - 1:
            block_values = block_values + ','
    block_values = block_values + '}'
    return block_name + block_values
# Generate TF_CONFIG from username and domain name. Use this to create an ENV variable which could be used as env in native service spec.
if len (sys.argv) != 6 :
    print "Usage: python generate_tf_config.py <username> <domain_name> <service_name> <num_workers (exclude master)> <num_ps>"
    sys.exit (1)
username = sys.argv[1]
domain =  sys.argv[2]
servicename = sys.argv[3]
num_worker = int(sys.argv[4])
num_ps = int(sys.argv[5])
hostname_suffix = "." + servicename + "."+ username + "." + domain + ":8000"
cluster = '"{' + "\\\\" + '\\"cluster' + "\\\\" + '\\":{'
master = get_component_array("master", 1, hostname_suffix) + ","
ps = get_component_array("ps", num_ps, hostname_suffix) + ","
worker = get_component_array("worker", num_worker, hostname_suffix) + "},"
task = get_key_value_pair("task", ["type", "index"], ["${COMPONENT_NAME}", "${COMPONENT_ID}"], 2) + ","
env = get_key_value_pair("environment", "", ["cloud"], 1) + "}" + '"'
print '"{}"'.format("TF_CONFIG"), ":" , cluster, master, ps, worker, task, env
```

Running

```
python path/to/saved/python-file <user_name> example.com distributed-tf 10 3
```

Generates ```TF_CONFIG``` for given user_name, domain name at example.com (which is same as ```hadoop.registry.dns.domain-name``` in ```yarn-site.xml```), service name is ```distributed-tf```. 10 workers (exclude master), and 3 parameter servers. The python script can be tailored according to your cluster environment.

#### Yarnfile
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
                "memory": "4096"
            },
            "artifact" : {
              "id" : <docker-image-name>,
              "type" : "DOCKER"
            },
            "launch_command": "export HADOOP_HDFS_HOME=/hadoop-3.1.0; export HADOOP_HOME=; export HADOOP_YARN_HOME=; export CLASSPATH=\\`\\$HADOOP_HDFS_HOME/bin/hadoop classpath --glob\\`; export LD_LIBRARY_PATH=\\$LD_LIBRARY_PATH:/usr/lib/jvm/java-8-openjdk-amd64/jre/lib/amd64/server/; cd /test/models/tutorials/image/cifar10_estimator && python cifar10_main.py --data-dir=hdfs://default/tmp/cifar-10-data --job-dir=hdfs://default/tmp/cifar-10-jobdir --train-steps=10000 --num-gpus=0 --eval-batch-size=16 --train-batch-size=16 --sync",
            "number_of_containers": 1,
            "run_privileged_container": false
        },
        {
            "name": "worker",
            "dependencies": [],
            "resource": {
                "cpus": 1,
                "memory": "4096"
            },
            "artifact" : {
              "id" : <docker-image-name>,
              "type" : "DOCKER"
            },
            "launch_command": "export HADOOP_HDFS_HOME=/hadoop-3.1.0; export HADOOP_HOME=; export HADOOP_YARN_HOME=; export CLASSPATH=\\`\\$HADOOP_HDFS_HOME/bin/hadoop classpath --glob\\`; export LD_LIBRARY_PATH=\\$LD_LIBRARY_PATH:/usr/lib/jvm/java-8-openjdk-amd64/jre/lib/amd64/server/; cd /test/models/tutorials/image/cifar10_estimator && python cifar10_main.py --data-dir=hdfs://default/tmp/cifar-10-data --job-dir=hdfs://default/tmp/cifar-10-jobdir --train-steps=10000 --num-gpus=0 --eval-batch-size=16 --train-batch-size=16 --sync",
            "number_of_containers": 1,
            "run_privileged_container": false
        },
        {
            "name": "ps",
            "dependencies": [],
            "resource": {
                "cpus": 1,
                "memory": "2048"
            },
            "artifact" : {
              "id" : <docker-image-name>,
              "type" : "DOCKER"
            },
            "launch_command": "export HADOOP_HDFS_HOME=/hadoop-3.1.0; export HADOOP_HOME=; export HADOOP_YARN_HOME=; export CLASSPATH=\\`\\$HADOOP_HDFS_HOME/bin/hadoop classpath --glob\\`; export LD_LIBRARY_PATH=\\$LD_LIBRARY_PATH:/usr/lib/jvm/java-8-openjdk-amd64/jre/lib/amd64/server/; cd /test/models/tutorials/image/cifar10_estimator  && ls -l && python cifar10_main.py --data-dir=hdfs://default/tmp/cifar-10-data --job-dir=hdfs://default/tmp/cifar-10-jobdir --num-gpus=0",
            "number_of_containers": 1,
            "run_privileged_container": false
        }
    ],
    "configuration": {
        "properties": {},
        "env": {
            "TF_CONFIG" : <TODO: Generated TF_CONFIG>,
            "HADOOP_CONF_DIR" : "/etc/hadoop/conf",
            "JAVA_HOME" : "/usr/lib/jvm/java-8-openjdk-amd64/jre/",
            "YARN_CONTAINER_RUNTIME_DOCKER_CONTAINER_NETWORK": "bridge"
        }
    }
}
```

Sample output of master:
```
...
allow_soft_placement: true
, '_tf_random_seed': None, '_task_type': u'master', '_environment': u'cloud', '_is_chief': True, '_cluster_spec': <tensorflow.python.training.server_lib.ClusterSpec object at 0x7fe77cb15050>, '_tf_config': gpu_options {
  per_process_gpu_memory_fraction: 1.0
}
...
2018-05-06 22:29:14.656022: I tensorflow/core/distributed_runtime/rpc/grpc_channel.cc:215] Initialize GrpcChannelCache for job master -> {0 -> localhost:8000}
2018-05-06 22:29:14.656097: I tensorflow/core/distributed_runtime/rpc/grpc_channel.cc:215] Initialize GrpcChannelCache for job ps -> {0 -> ps-0.distributed-tf.root.hwxdev.site:8000}
2018-05-06 22:29:14.656112: I tensorflow/core/distributed_runtime/rpc/grpc_channel.cc:215] Initialize GrpcChannelCache for job worker -> {0 -> worker-0.distributed-tf.root.hwxdev.site:8000}
2018-05-06 22:29:14.659359: I tensorflow/core/distributed_runtime/rpc/grpc_server_lib.cc:316] Started server with target: grpc://localhost:8000
...
INFO:tensorflow:Restoring parameters from hdfs://default/tmp/cifar-10-jobdir/model.ckpt-0
INFO:tensorflow:Evaluation [1/625]
INFO:tensorflow:Evaluation [2/625]
INFO:tensorflow:Evaluation [3/625]
INFO:tensorflow:Evaluation [4/625]
INFO:tensorflow:Evaluation [5/625]
INFO:tensorflow:Evaluation [6/625]
...
INFO:tensorflow:Validation (step 1): loss = 1220.6445, global_step = 1, accuracy = 0.1
INFO:tensorflow:loss = 6.3980675, step = 0
INFO:tensorflow:loss = 6.3980675, learning_rate = 0.1
INFO:tensorflow:global_step/sec: 2.34092
INFO:tensorflow:Average examples/sec: 1931.22 (1931.22), step = 100
INFO:tensorflow:Average examples/sec: 354.236 (38.6479), step = 110
INFO:tensorflow:Average examples/sec: 211.096 (38.7693), step = 120
INFO:tensorflow:Average examples/sec: 156.533 (38.1633), step = 130
INFO:tensorflow:Average examples/sec: 128.6 (38.7372), step = 140
INFO:tensorflow:Average examples/sec: 111.533 (39.0239), step = 150
```

Sample output of worker:
```
, '_tf_random_seed': None, '_task_type': u'worker', '_environment': u'cloud', '_is_chief': False, '_cluster_spec': <tensorflow.python.training.server_lib.ClusterSpec object at 0x7fc2a490b050>, '_tf_config': gpu_options {
  per_process_gpu_memory_fraction: 1.0
}
...
2018-05-06 22:28:45.807936: I tensorflow/core/distributed_runtime/rpc/grpc_channel.cc:215] Initialize GrpcChannelCache for job master -> {0 -> master-0.distributed-tf.root.hwxdev.site:8000}
2018-05-06 22:28:45.808040: I tensorflow/core/distributed_runtime/rpc/grpc_channel.cc:215] Initialize GrpcChannelCache for job ps -> {0 -> ps-0.distributed-tf.root.hwxdev.site:8000}
2018-05-06 22:28:45.808064: I tensorflow/core/distributed_runtime/rpc/grpc_channel.cc:215] Initialize GrpcChannelCache for job worker -> {0 -> localhost:8000}
2018-05-06 22:28:45.809919: I tensorflow/core/distributed_runtime/rpc/grpc_server_lib.cc:316] Started server with target: grpc://localhost:8000
...
INFO:tensorflow:loss = 5.319096, step = 0
INFO:tensorflow:loss = 5.319096, learning_rate = 0.1
INFO:tensorflow:Average examples/sec: 49.2338 (49.2338), step = 10
INFO:tensorflow:Average examples/sec: 52.117 (55.3589), step = 20
INFO:tensorflow:Average examples/sec: 53.2754 (55.7541), step = 30
INFO:tensorflow:Average examples/sec: 53.8388 (55.6028), step = 40
INFO:tensorflow:Average examples/sec: 54.1082 (55.2134), step = 50
INFO:tensorflow:Average examples/sec: 54.3141 (55.3676), step = 60
```

Sample output of ps:
```
...
, '_tf_random_seed': None, '_task_type': u'ps', '_environment': u'cloud', '_is_chief': False, '_cluster_spec': <tensorflow.python.training.server_lib.ClusterSpec object at 0x7f4be54dff90>, '_tf_config': gpu_options {
  per_process_gpu_memory_fraction: 1.0
}
...
2018-05-06 22:28:42.562316: I tensorflow/core/distributed_runtime/rpc/grpc_channel.cc:215] Initialize GrpcChannelCache for job master -> {0 -> master-0.distributed-tf.root.hwxdev.site:8000}
2018-05-06 22:28:42.562408: I tensorflow/core/distributed_runtime/rpc/grpc_channel.cc:215] Initialize GrpcChannelCache for job ps -> {0 -> localhost:8000}
2018-05-06 22:28:42.562433: I tensorflow/core/distributed_runtime/rpc/grpc_channel.cc:215] Initialize GrpcChannelCache for job worker -> {0 -> worker-0.distributed-tf.root.hwxdev.site:8000}
2018-05-06 22:28:42.564242: I tensorflow/core/distributed_runtime/rpc/grpc_server_lib.cc:316] Started server with target: grpc://localhost:8000
```