#!/usr/bin/env python
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Usage and Assumptions:
# presetup-tf.sh uploaded to hdfs under $remote_conf_path

import argparse
import json
import os


def get_component_array(name, count, hostname_suffix):
    component = '\\"' + name + '\\":'
    component_names = '['
    for i in xrange(0, count):
        component_names = component_names + '\\' + '"' + name + "-" + str(
            i) + hostname_suffix + '\\"'
        if i != count - 1:
            component_names = component_names + ','
    component_names = component_names + ']'
    return component + component_names


def get_key_value_pair(name, keys, values, count):
    block_name = '\\"' + name + '\\":'
    block_values = ''
    if count == 1:
        block_values = block_values + '\\' + '"' + values[0] + '\\"'
        return block_name + block_values
    block_values = '{'
    for i in xrange(0, count):
        block_values = block_values + '\\' + '"' + keys[i] + '\\"' + ':' + \
                       values[i]
        if i != count - 1:
            block_values = block_values + ','
    block_values = block_values + '}'
    return block_name + block_values


def handle_tf_config_env(tf_json, username, domain):
    if username is None or username == '':
        raise Exception("Empty username specified, please double check")
    if domain is None or domain == '':
        raise Exception("Empty domain name specified, please double check")

    num_worker = -1
    num_ps = -1
    num_master = -1

    # Do we need to generate tf_config? First get unique component names
    for c in tf_json['components']:
        name = c['name']
        if name == 'worker':
            num_worker = int(c['number_of_containers'])
        elif name == 'ps':
            num_ps = int(c['number_of_containers'])
        elif name == 'master':
            num_master = int(c['number_of_containers'])

    if num_worker < 0 or num_ps < 0 or num_master != 1:
        raise Exception(
            "Should include worker/ps/master, all with >0 number_of_containers. "
            "Master's number_of_containers must be equal to 1")

    hostname_suffix = "." + tf_json[
        'name'] + "." + username + "." + domain + ":8000"
    cluster = '{' + '\\"cluster' + '\\":{'
    master = get_component_array("master", 1, hostname_suffix) + ","
    ps = get_component_array("ps", num_ps, hostname_suffix) + ","
    worker = get_component_array("worker", num_worker, hostname_suffix) + "},"
    component_name = '\\"' + "${COMPONENT_NAME}" + '\\"'
    component_id = "${COMPONENT_ID}"
    task = get_key_value_pair("task", ["type", "index"],
                              [component_name, component_id], 2) + ","
    environment = get_key_value_pair("environment", "", ["cloud"], 1) + "}"
    tf_config_op = cluster + master + ps + worker + task + environment
    tf_json['configuration']['env']['TF_CONFIG'] = tf_config_op


if __name__ == "__main__":
    # Instantiate the parser
    parser = argparse.ArgumentParser(
        description='Submit Tensorflow job to YARN.')

    # Required positional argument
    parser.add_argument('-remote_conf_path', type=str,
                        help='Remote Configuration path to run TF job',
                        required=True)
    parser.add_argument('-input_spec', type=str,
                        help='Yarnfile specification for TF job.',
                        required=True)
    parser.add_argument('--docker_image', type=str,
                        help='Docker image name for TF job.', required=False)
    parser.add_argument('--env', type=str,
                        help='Environment variables needed for TF job in'
                             ' key=value format.',
                        required=False)
    parser.add_argument('--submit', action='store_true',
                        help='Automatically submit TF job to YARN, if this is '
                             'not specified. New generated spec will be printed '
                             'to <stdout>, and user can use yarn app -launch '
                             '<path/to/spec> to launch it later.')
    parser.add_argument('--job_name', type=str,
                        help='Specify job name of the Tensorflow job, which '
                             'will overwrite the one specified in input spec '
                             'file',
                        required=False)
    parser.add_argument('--gpu', type=str, default=0,
                        help='Specify number of GPUs needed per component.',
                        required=False)
    parser.add_argument('--user', type=str,
                        help='Specify user name if it is different from $USER '
                             '(e.g. kinit user)',
                        required=False)
    parser.add_argument('--domain', type=str,
                        help='Cluster domain name, which should be same as '
                             'hadoop.registry.dns.domain-name in yarn-site.xml'
                             ', required for distributed Tensorflow',
                        required=False)
    parser.add_argument('--distributed', action='store_true',
                        help='Running distributed tensorflow, if this is '
                             'specified, worker/ps/master must be included '
                             'inside input spec')
    parser.add_argument('--kerberos', action='store_true',
                        help='Is this a kerberos-enabled cluster or not')
    parser.add_argument('--verbose', action='store_true',
                        help='Print debug information')
    args = parser.parse_args()

    remote_path = args.remote_conf_path
    input_json_spec = args.input_spec
    submit_to_yarn = args.submit
    envs_array = []
    if hasattr(args, 'env'):
        envs = args.env
        if envs is not None:
            envs_array = envs.split(',')
    verbose = args.verbose
    if hasattr(args, 'user'):
        user = args.user
    if user is None:
        user = os.environ['USER']
    if hasattr(args, 'domain'):
        domain = args.domain
    if hasattr(args, 'job_name'):
        job_name = args.job_name
    distributed = args.distributed
    docker_image = None
    if hasattr(args, 'docker_image'):
        docker_image = args.docker_image
    kerberos = args.kerberos
    num_gpu = -1
    if hasattr(args, 'gpu'):
        num_gpu = int(args.gpu)

    # Only print when verbose
    if verbose:
        print "remote_path=", remote_path
        print "input_spec_file=", input_json_spec
        print "submit=", submit_to_yarn
        print "user=", user
        print "gpu=", num_gpu

    with open(input_json_spec) as json_file:
        data = json_file.read()
    tf_json = json.loads(data)

    if job_name is not None:
        tf_json['name'] = job_name

    # Updating per-component commands with presetup-tf.sh
    for component in tf_json['components']:
        # Append presetup-tf.sh to launch command
        launch_cmd = '. resources/presetup-tf.sh && ' + component['launch_command'] \
                    + ' --num-gpus=' + `num_gpu`
        component['launch_command'] = launch_cmd

        if verbose:
            print "New launch command = ", launch_cmd

        if docker_image is not None and len(docker_image) > 0:
            component['artifact'] = {}
            component['artifact']['id'] = docker_image
            component['artifact']['type'] = "DOCKER"

            if verbose:
                print "Using docker image=", docker_image

        if num_gpu > 0:
            component['resource']['additional'] = {}
            component['resource']['additional']['yarn.io/gpu'] = {}
            component['resource']['additional']['yarn.io/gpu']['value'] = num_gpu
        artifact = component.get('artifact')
        if artifact is None or artifact.get('id') is None:
            raise Exception("Docker image for components doesn't set, please"
                            " either set it in input spec or by passing "
                            "--docker-image <image-name> commandline")

    if distributed:
        handle_tf_config_env(tf_json, user, domain)

    # Update conf files to mount in files section.
    spec_envs = tf_json['configuration']['env']
    docker_mounts = ''

    if spec_envs is not None and \
            spec_envs.get('YARN_CONTAINER_RUNTIME_DOCKER_MOUNTS') is not None:
        docker_mounts = spec_envs['YARN_CONTAINER_RUNTIME_DOCKER_MOUNTS']

    srcfiles, destfiles = [], []
    srcfiles.append(remote_path + '/configs/core-site.xml')
    srcfiles.append(remote_path + '/configs/hdfs-site.xml')
    srcfiles.append(remote_path + '/configs/presetup-tf.sh')
    destfiles.append("core-site.xml")
    destfiles.append("hdfs-site.xml")
    destfiles.append("presetup-tf.sh")

    if len(docker_mounts) > 0:
        docker_mounts = docker_mounts + ","
    docker_mounts = docker_mounts + \
                    "resources/core-site.xml:/etc/hadoop/conf/core-site.xml:ro," \
                    "resources/hdfs-site.xml:/etc/hadoop/conf/hdfs-site.xml:ro"

    if kerberos:
        srcfiles.append(remote_path + '/configs/krb5.conf')
        destfiles.append('krb5.conf')
        docker_mounts = docker_mounts + ",resources/krb5.conf:/etc/krb5.conf:ro"

    docker_mounts = docker_mounts + ",/etc/passwd:/etc/passwd:ro" + \
                    ",/etc/group:/etc/group:ro"
    file_envs = [{"type": "STATIC", "dest_file": d, "src_file": s} for d, s in
                 zip(destfiles, srcfiles)]
    tf_json['configuration']['files'] = file_envs

    envs_array.append('YARN_CONTAINER_RUNTIME_DOCKER_MOUNTS=' + docker_mounts)

    # Fetch all envs passed and update in common configuration section
    for env in envs_array:
        if verbose:
            print "Setting env=", env
        key_value = env.split('=')
        tf_json['configuration']['env'][key_value[0]] = key_value[1]

    # submit to YARN
    if submit_to_yarn:
        # todo
        print("Submit job to YARN.")
    else:
        jstr = json.dumps(tf_json, sort_keys=False, indent=2)
        print(jstr)
