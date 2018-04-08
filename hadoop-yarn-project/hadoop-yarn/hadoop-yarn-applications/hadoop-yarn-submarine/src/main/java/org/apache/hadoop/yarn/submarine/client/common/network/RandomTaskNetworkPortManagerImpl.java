/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. See accompanying LICENSE file.
 */

package org.apache.hadoop.yarn.submarine.client.common.network;

import java.util.Random;

public class RandomTaskNetworkPortManagerImpl implements TaskNetworkPortManager {
  @Override
  public int getPort(String serviceName, String taskType, int index) {
    // Get port 49152-59152 according to IANA recommendations of ephermeral port
    Random random = new Random(
        serviceName.hashCode() * 37 + taskType.hashCode() * 19 + index);
    int port = 49152 + random.nextInt(10000);
    return port;
  }
}
