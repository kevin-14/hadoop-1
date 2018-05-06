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


# Example Usage:
#   python generate_tf_config.py <user_name> example.com distributed-tf 10 3
# Generates TF_CONFIG for given user_name, domain name at example.com
# (which is same as hadoop.registry.dns.domain-name in yarn-site.xml),
# service name is distributed-tf. 10 workers (exclude master), and 3 parameter
# servers. The python script can be tailored according to your cluster
# environment.

import code
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

# Generate TF_CONFIG from username and domainname. Use this to create an ENV variable which could be used as env in native service spec.
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
task = "\\{}\\".format("") +  '\\"task' + "\\{}\\".format("") + '\\":'
types = '{' + "\\{}\\".format("") +  '\\"type' + "\\{}\\".format("") + '\\":' + "\\{}\\".format("") + '\\"$' + '{COMPONENT_NAME}' + "\\{}\\".format("") + '\\",'
index = "\\{}\\".format("") +  '\\"index' + "\\{}\\".format("") + '\\":' + '$' + '{COMPONENT_ID}' + '},'
env = "\\{}\\".format("") +  '\\"environment' + "\\{}\\".format("") + '\\":'  +"\\{}\\".format("") +  '\\"cloud' + "\\{}\\".format("") + '\\"}"'
print '"{}"'.format("TF_CONFIG"), ":" , cluster, master, ps, worker, task, types, index, env