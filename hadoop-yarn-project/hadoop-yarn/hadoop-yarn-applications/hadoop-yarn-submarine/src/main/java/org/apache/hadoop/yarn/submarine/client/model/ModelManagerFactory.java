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

package org.apache.hadoop.yarn.submarine.client.model;

import org.apache.hadoop.yarn.exceptions.YarnException;
import org.apache.hadoop.yarn.submarine.client.common.ClientContext;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class ModelManagerFactory {
  public static ModelManager getModelManager(String servingFramework,
      ClientContext clientContext) throws YarnException {
    if (null != servingFramework && (!servingFramework.equals(
        "simple_serving_framework"))) {
      try {
        Class cl = Class.forName("servingFramework");
        Constructor con = cl.getConstructor();
        Object obj = con.newInstance();
        return (ModelManager) obj;
      } catch (NoSuchMethodException e) {
        throw new YarnException(e);
      } catch (IllegalAccessException e) {
        e.printStackTrace();
      } catch (InstantiationException e) {
        e.printStackTrace();
      } catch (InvocationTargetException e) {
        e.printStackTrace();
      } catch (ClassNotFoundException e) {
        e.printStackTrace();
      }
    } else {
      return new STSModelManager();
    }
  }
}
