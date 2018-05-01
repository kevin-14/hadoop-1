Single node TF
--------------

Prepare
=======

(Following steps need to be done on all your NMs)
0) Prerequisite: install Tensorflow on all your machines

1) Checkout https://github.com/tensorflow/models/
2) Go to `tutorials/image/cifar10_estimator`
3) Generate data 

```
python generate_cifar10_tfrecords.py --data-dir=/tmp/cifar-10-data
```

4) Upload data to HDFS

```
hadoop fs -put /tmp/cifar-10-data/ /tmp/
```

5) Directly use docker to run: 
```
nvidia-docker run -e HADOOP_CONF_DIR=/etc/hadoop/conf -e JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64/jre/ -e HADOOP_HDFS_HOME=/hadoop-3.1.0 -it wtan/tf-on-yarn-example:1.3.0-gpu /bin/bash -c "cd /test/models/tutorials/image/cifar10_estimator && CLASSPATH=\`\$HADOOP_HDFS_HOME/bin/hadoop classpath --glob\` LD_LIBRARY_PATH=\$LD_LIBRARY_PATH:/usr/lib/jvm/java-8-openjdk-amd64/jre/lib/amd64/server/ python cifar10_main.py --data-dir=hdfs://default/tmp/cifar-10-data --job-dir=hdfs://default/tmp/cifar-10-jobdir --num-gpus=1 --train-steps=40000 --sync"

nvidia-docker run -v /tmp/krb5cc_0:/tmp/krb5cc_0 -v /etc/krb5.conf:/etc/krb5.conf -it wtane:1.3.0-gpu /bin/bash -c "cd /test/models/tutorials/image/cifar10_estimator && export HADOOP_HDFS_HOME=/hadoop-3.1.0; export HADOOP_CONF_DIR=/etc/hadoop/conf; export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64/jre/; export CLASSPATH=\`\$HADOOP_HDFS_HOME/bin/hadoop classpath --glob\`; export LD_LIBRARY_PATH=\$LD_LIBRARY_PATH:/usr/lib/jvm/java-8-openjdk-amd64/jre/lib/amd64/server/; python cifar10_main.py --data-dir=hdfs://default/tmp/cifar-10-data --job-dir=hdfs://default/tmp/cifar-10-jobdir --num-gpus=0 --train-steps=40000 --sync"

nvidia-docker run -v /tmp/krb5cc_0:/tmp/krb5cc_0 -v /etc/krb5.conf:/etc/krb5.conf -it wtan/tf-on-yarn-example:1.3.0-gpu /bin/bash -c "cd /test/models/tutorials/image/cifar10_estimator && export HADOOP_HDFS_HOME=/hadoop-3.1.0; export HADOOP_CONF_DIR=/etc/hadoop/conf; export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64/jre/; export CLASSPATH=\`\$HADOOP_HDFS_HOME/bin/hadoop classpath --glob\`; export LD_LIBRARY_PATH=\$LD_LIBRARY_PATH:/usr/lib/jvm/java-8-openjdk-amd64/jre/lib/amd64/server/; python cifar10_main.py --data-dir=hdfs://default/tmp/cifar-10-data --job-dir=hdfs://default/tmp/cifar-10-jobdir --num-gpus=1 --train-batch-size=16 --train-steps=40000"
```

5) Run following job
```
yarn application -destroy distributed-tf ; curl --negotiate -u: -H "Content-Type: application/json" -X POST http://ctr-e138-1518143905142-261690-01-000004.hwx.site:8088/app/v1/services?doAs=ambari-qa -d '{
    "name": "distributed-tf",
    "version": "1.0.0",
    "components": [
        {
            "artifact" : {
              "id" : "wtan/tf-on-yarn-example:1.3.0-gpu-003",
              "type" : "DOCKER"
            },
            "name": "worker",
            "dependencies": [],
            "resource": {
                "cpus": 1,
                "memory": "2048",
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
}'
```

Distributed TF
--------------

```
yarn application -destroy distributed-tf ; curl --negotiate -u: -H "Content-Type: application/json" -X POST http://ctr-e138-1518143905142-261690-01-000004.hwx.site:8088/app/v1/services?doAs=ambari-qa -d '{
    "name": "distributed-tf",
    "version": "1.0.0",
    "components": [
        {
            "name": "master",
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
            "launch_command": "python /test-dtf/models/tutorials/image/cifar10_estimator/cifar10_main.py --data-dir=hdfs://default/tmp/cifar-10-data --job-dir=hdfs://default/data --num-gpus=1  --train-steps=40000  --sync",
            "number_of_containers": 1,
            "run_privileged_container": false
        },
        {
            "name": "worker",
            "dependencies": [],
            "resource": {
                "cpus": 1,
                "memory": "2048",
                "additional" : {
                  "yarn.io/gpu" : {
                    "value" : 2
                   }
                }
            },
            "launch_command": "python /test-dtf/models/tutorials/image/cifar10_estimator/cifar10_main.py --data-dir=hdfs://default/tmp/cifar-10-data --job-dir=hdfs://ctr-e138-1518143905142-261690-01-000003.hwx.site:9000/data --num-gpus=1 --train-steps=40000 --sync",
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
            "launch_command": "python /test-dtf/models/tutorials/image/cifar10_estimator/cifar10_main.py --job-dir=hdfs://ctr-e138-1518143905142-261690-01-000003.hwx.site:9000/data",
            "number_of_containers": 1,
            "run_privileged_container": false
        }
    ],
    "configuration": {
        "properties": {},
        "env": {
            "TF_CONFIG": "{ \\\"cluster\\\": { \\\"master\\\": [\\\"master-0.distributed-tf.ambari-qa.test.com:8000\\\"], \\\"ps\\\": [\\\"ps-0.distributed-tf.ambari-qa.test.com:8000\\\"], \\\"worker\\\": [\\\"worker-0.distributed-tf.ambari-qa.test.com:8000\\\"] }, \\\"task\\\": { \\\"type\\\": \\\"${COMPONENT_NAME}\\\", \\\"index\\\": ${COMPONENT_ID} }, \\\"environment\\\": \\\"cloud\\\" }",
           "HADOOP_CONF_DIR" : "/etc/hadoop/conf/",
	       "LD_LIBRARY_PATH" : "$LD_LIBRARY_PATH:/base/tools/jdk1.8.0_112/jre/lib/amd64/server/",
	       "HADOOP_HOME" : "/usr/hdp/3.0.0.0-1251/hadoop/",
	       "JAVA_HOME" : "/base/tools/jdk1.8.0_112/jre/",
	       "HADOOP_HDFS_HOME" : "/usr/hdp/3.0.0.0-1251/hadoop/",
	       "HADOOP_YARN_HOME" : ""
        }
    },
    "kerberos_principal" : {
      "principal_name" : "ambari-qa@EXAMPLE.COM",
      "keytab" : "file:///etc/security/keytabs/smokeuser.headless.keytab"
    }
}'
```
