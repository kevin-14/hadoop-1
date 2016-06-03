/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.yarn.server.resourcemanager.scheduler.common;

import org.apache.commons.collections.MapUtils;
import org.apache.hadoop.HadoopIllegalArgumentException;
import org.apache.hadoop.yarn.api.records.Priority;
import org.apache.hadoop.yarn.util.ConverterUtils;

import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.Map;

public class RMPlacementStrategy {
  private Operator op;
  private PlacementSetType setType;
  private Map<TargetType, Object> targets;

  private static final RMPlacementStrategy DEFAULT_RM_PLACEMENT_STRATEGY =
      new RMPlacementStrategy() {
        @Override
        public Operator getOp() {
          return Operator.NO;
        }

        @Override
        public PlacementSetType getPlacementSetType() {
          return PlacementSetType.HOST;
        }

        @Override
        public Map<TargetType, Object> getTargets() {
          return MapUtils.EMPTY_MAP;
        }
      };

  public enum Operator {
    NO,
    AFFINITY,
    ANTI_AFFINITY,
  }

  public enum PlacementSetType {
    HOST,
    // RACK,
    // PARTITION
  }

  public enum TargetType {
    APPLICATION,
    PRIORITY,
  }

  /**
   * Input string format like:
   *
   * "op=AFFINITY, placement-set-type=HOST, \
   *  targets=[application=application_12345_1;priority=1]"
   */
  public static RMPlacementStrategy fromString(String string)
      throws IllegalFormatException {
    if (null == string || string.isEmpty()) {
      return DEFAULT_RM_PLACEMENT_STRATEGY;
    }

    Operator op = Operator.NO;
    PlacementSetType setType = PlacementSetType.HOST;
    Map<TargetType, Object> targets = MapUtils.EMPTY_MAP;

    string = string.replaceAll("\\s+","");
    for (String part : string.split(",")) {
      if (part.contains("=")) {
        String key = part.substring(0, part.indexOf('=')).toLowerCase();
        String value = part.substring(part.indexOf('=') + 1);

        if (key.equals("op")) {
          op = Operator.valueOf(value.toUpperCase());
        } else if (key.equals("placement-set-type")) {
          setType = PlacementSetType.valueOf(value.toUpperCase());
        } else if (key.equals("targets")) {
          targets = new HashMap<>();

          value = value.substring(value.indexOf('[') + 1, value.indexOf(']'))
              .toLowerCase();
          if (value.isEmpty()) {
            continue;
          }
          for (String t : value.split(";")) {
            String tk = t.substring(0, t.indexOf('=')).toLowerCase();
            String tv = t.substring(t.indexOf('=') + 1);
            if (tk.equals("application")) {
              targets.put(TargetType.APPLICATION,
                  ConverterUtils.toApplicationId(tv));
            } else if (tk.equals("priority")) {
              targets.put(TargetType.PRIORITY,
                  Priority.newInstance(Integer.valueOf(Integer.parseInt(tv))));
            } else {
              throw new HadoopIllegalArgumentException(
                  "Unknown target:" + string);
            }
          }
        } else {
          throw new HadoopIllegalArgumentException("Unknown key:" + string);
        }
      } else {
        throw new HadoopIllegalArgumentException(
            "Each part should contains '=':" + string);
      }
    }

    return new RMPlacementStrategy(op, setType, targets);
  }

  public RMPlacementStrategy() {

  }

  public RMPlacementStrategy(Operator op, PlacementSetType type,
      Map<TargetType, Object> targets) {
    this.op = op;
    this.setType = type;
    this.targets = targets;
  }

  public Operator getOp() {
    return op;
  }

  public PlacementSetType getPlacementSetType() {
    return setType;
  }

  public Map<TargetType, Object> getTargets() {
    return targets;
  }
}