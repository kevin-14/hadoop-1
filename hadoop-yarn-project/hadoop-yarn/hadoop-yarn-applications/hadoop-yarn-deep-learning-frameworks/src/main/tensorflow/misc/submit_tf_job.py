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
# 1. presetup-tf.sh uploaded to hdfs under $remote_conf_path
# 2. /scripts dir is created in dockerfile to keep presetup-tf.sh

import code
import sys
import argparse
import json
import subprocess

def get_component_array(name, count, hostname_suffix):
    component = '\\"' + name + '\\":'
    component_names = '['
    for i in xrange(0, count):
        component_names = component_names + '\\' + '"' + name + "-" + str(i) + hostname_suffix + '\\"'
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
        block_values = block_values + '\\' + '"' + keys[i] + '\\"' + ':' +  values[i]
        if i != count - 1:
            block_values = block_values + ','
    block_values = block_values + '}'
    return block_name + block_values

# Instantiate the parser
parser = argparse.ArgumentParser(description='Submit Tensorflow job to YARN.')

# Required positional argument
parser.add_argument('-remote_conf_path', type=str,
                    help='Remote Configuration path to run TF job', required=True)
parser.add_argument('-input_spec', type=str,
                    help='Yarnfile specification for TF job.', required=True)
parser.add_argument('-image_name', type=str,
                    help='Docker image name for TF job.', required=True)
parser.add_argument('-env', type=str,
                    help='Environment variables needed for TF job in key=value format.', required=True)
parser.add_argument('-tf_config_params', type=str,
                    help='Input params needed for TF_CONFIG env in <username>:<domain_name>:<service_name>:<num_workers (exclude master)>:<num_ps> format.', required=True)
parser.add_argument('--submit', action='store_true',
                    help='Automatically submit TF job to YARN.')
args = parser.parse_args()

remote_path = args.remote_conf_path
input_json_spec = args.input_spec
submit_to_yarn = args.submit
image_name = args.image_name
envs = args.env
envs_array = envs.split(':')
tf_config_params = args.tf_config_params
tf_config_params_array = tf_config_params.split(':', 5 )

if len (tf_config_params_array) != 5 :
    print "Usage: tf_config_params to provide param as <username>:<domain_name>:<service_name>:<num_workers (exclude master)>:<num_ps>"
    sys.exit (1)

# To Delete
print(remote_path)
print(input_json_spec)
print(submit_to_yarn)
print(tf_config_params)

with open(input_json_spec) as json_file:
    data = json_file.read()
tf_json = json.loads(data)

# Updating launch-command with presetup-tf.sh
for component in tf_json['components']:
    launch_cmd = '. /scripts/presetup-tf.sh;' + component['launch_command']
    component['launch_command'] = launch_cmd
    component['artifact'] = {}
    component['artifact']['id'] = image_name
    component['artifact']['type'] = "DOCKER"

# create an env section under config
tf_json['configuration']['env'] = {}

# Update TF_CONFIG and related env variables.
username = tf_config_params_array[0]
domain =  tf_config_params_array[1]
servicename = tf_config_params_array[2]
num_worker = int(tf_config_params_array[3])
num_ps = int(tf_config_params_array[4])
hostname_suffix = "." + servicename + "."+ username + "." + domain + ":8000"
cluster = '{' + '\\"cluster' + '\\":{'
master = get_component_array("master", 1, hostname_suffix) + ","
ps = get_component_array("ps", num_ps, hostname_suffix) + ","
worker = get_component_array("worker", num_worker, hostname_suffix) + "},"
component_name = '\\"' + "${COMPONENT_NAME}" + '\\"'
component_id = "${COMPONENT_ID}"
task = get_key_value_pair("task", ["type", "index"], [component_name, component_id], 2) + ","
env = get_key_value_pair("environment", "", ["cloud"], 1) + "}"
tf_config_op = cluster + master + ps + worker + task + env
tf_json['configuration']['env']['TF_CONFIG'] = tf_config_op

# Fetch all envs passed and update in common configuration section
for env in envs_array:
    key_value = env.split('=')
    tf_json['configuration']['env'][key_value[0]] = key_value[1]

# Update conf files to mount in files section.
srcfiles, destfiles = [], []
srcfiles.append(remote_path + '/configs/core-site.xml')
srcfiles.append(remote_path + '/configs/hdfs-site.xml')
destfiles.append("/configs/core-site.xml")
destfiles.append("/configs/hdfs-site.xml")
file_envs = [{"type": "STATIC", "dest_file": d, "src_file": s} for d, s in zip(destfiles, srcfiles)]
tf_json['configuration']['files'] = file_envs

# submit to YARN
if submit_to_yarn :
    # todo
    print("Submit job to YARN.")
else:
    jstr = json.dumps(tf_json, sort_keys=False, indent=2)
    print(jstr)