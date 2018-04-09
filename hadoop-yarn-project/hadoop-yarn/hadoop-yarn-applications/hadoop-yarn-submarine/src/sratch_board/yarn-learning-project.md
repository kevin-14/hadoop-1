TODO:
* Generate script of each task before launch. (for CLASSPATH, TensorBoard, etc.)

yalp
 job
   run
   --name
   --type
   --input
   --output
   --num-workers
   --num-ps
   --worker-resources
   --ps-resources
   --docker-image <docker-image>
   --queue <queue-name>
   --tensorboard
   --cmd (could use {{input}}, etc. to do substitute)
   ls
   --name
   --details
   stop
   --name
   restart
   --name
   delete
   --name
 model
   ls
   --name
     output models w/ jobs basic info and TS
   serve
   --name / SHA
   --<look at simple-tensor-serving doc> 


Scratch board:
--
wtan/tf-1.6.0-cuda9:0.0.1

cd /test/models/tutorials/image/cifar10_estimator/ && python generate_cifar10_tfrecords.py --data-dir=cifar-10-data && python cifar10_main.py --data-dir=cifar-10-data --job-dir=/tmp/cifar10 --num-gpus=2 --train-steps=1000

*Single node run command* 
```
yarn yalp job run --docker_image wtan/tf-1.7.0-cuda9:0.0.1 --name wangda-tf-job-16 --num_workers 1 --output /tmp/cifar10 --tensorboard true --worker_resources memory=20480,vcores=32,yarn.io/gpu=2 --worker_launch_cmd "cd /test/models/tutorials/image/cifar10_estimator/ && PWD=`pwd` && echo ${PWD} && python generate_cifar10_tfrecords.py --data-dir=cifar-10-data && python cifar10_main.py --data-dir=cifar-10-data --job-dir=/tmp/cifar10 --num-gpus=2 --train-steps=1000" --env LD_LIBRARY_PATH=$LD_LIBRARY_PATH:/usr/local/nvidia/lib64/
```

Tensorflow 1.7.0
```
Environments
  export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64/jre/
  export HADOOP_HDFS_HOME=/hadoop-3.1.0
  export HADOOP_CONF_DIR=/hadoop-3.1.0/etc/hadoop
```

```
yarn yalp job run --docker_image wtan/tf-1.7.0-cuda9-ubuntu:0.0.1 --name wangda-tf-job-16 --num_workers 1 --output /tmp/cifar10 --tensorboard true --worker_resources memory=20480,vcores=32,yarn.io/gpu=2 --worker_launch_cmd "cd /test/models/tutorials/image/cifar10_estimator/ && python cifar10_main.py --data-dir=/tmp/cifar-10-data --job-dir=/tmp/cifar10 --num-gpus=2 --train-steps=1000" --env LD_LIBRARY_PATH=$LD_LIBRARY_PATH:/usr/local/nvidia/lib64/
```

```
Census: 
cd /test/models/yarn/census_train_and_eval && python trainer/task.py --train-files census_data/adult.data.csv --eval-files census_data/adult.test.csv --train-steps 100 --job-dir hdfs://default/tmp/job

yarn submarine job run --name wangda-tf-job-16 --docker_image wtan/tf-1.7.0-cuda9-ubuntu-cpu:0.0.1 --num_workers 1 --tensorboard true --worker_resources memory=20480,vcores=32,yarn.io/gpu=2 --job_dir hdfs://default/tmp/job16 --worker_launch_cmd "cd /test/models/yarn/census_train_and_eval && python trainer/task.py --train-files census_data/adult.data.csv --eval-files census_data/adult.test.csv --train-steps 100 --job-dir %job_dir%" --env LD_LIBRARY_PATH=$LD_LIBRARY_PATH:/usr/local/nvidia/lib64/ --env JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64/jre/ --env HADOOP_HDFS_HOME=/hadoop-3.1.0 --env HADOOP_CONF_DIR=/hadoop-3.1.0/etc/hadoop/ --env HADOOP_HOME= --env HADOOP_YARN_HOME=
```
--

*Dockerfile*
--
```
FROM nvidia/cuda:9.0-cudnn7-runtime-centos7

RUN yum -y install epel-release
RUN yum -y install git gcc gcc-c++ python-pip python-devel atlas atlas-devel gcc-gfortran openssl-devel libffi-devel
RUN pip install --upgrade tensorflow-gpu
RUN mkdir /test
RUN cd /test && git clone https://github.com/dsindex/tensorflow
RUN cd /test && git clone https://github.com/tensorflow/models/

# Install Hadoop (TODO, should use Apache version)
RUN curl http://s3.amazonaws.com/dev.hortonworks.com/HDP/centos7/3.x/BUILDS/3.0.0.0-1089/hdpbn.repo > /etc/yum.repos.d/hdp.repo
RUN yum install hadoop* java-1.8.0-openjdk.x86_64 -y
RUN export JAVA_HOME=/usr/lib/jvm/java-1.8.0-openjdk-1.8.0.161-0.b14.el7_4.x86_64/jre/

COPY libcudnn.so* /usr/local/cuda/lib64/

RUN chown -R nobody /test
```
--

*YALP script*
--
```
  case ${subcmd} in
    yalp)
      HADOOP_CLASSNAME=Cli
      set -- "${subcmd}" "$@"
      HADOOP_SUBCMD_ARGS=("${@:2}")
      local sld="${HADOOP_YARN_HOME}/${YARN_DIR},\
${HADOOP_YARN_HOME}/${YARN_LIB_JARS_DIR},\
${HADOOP_HDFS_HOME}/${HDFS_DIR},\
${HADOOP_HDFS_HOME}/${HDFS_LIB_JARS_DIR},\
${HADOOP_COMMON_HOME}/${HADOOP_COMMON_DIR},\
${HADOOP_COMMON_HOME}/${HADOOP_COMMON_LIB_JARS_DIR}"
      hadoop_translate_cygwin_path sld
      hadoop_add_param HADOOP_OPTS service.libdir "-Dservice.libdir=${sld}"
    ;;
```
--