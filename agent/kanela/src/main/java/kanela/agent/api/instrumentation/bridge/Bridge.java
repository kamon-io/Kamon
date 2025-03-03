/*
 * =========================================================================================
 * Copyright © 2013-2018 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */
package kanela.agent.api.instrumentation.bridge;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotation to mark a method that will be used as a bridge to call a target method specified in
 * the annotation value, based on [1].
 *
 * <p>[1]
 * https://github.com/glowroot/glowroot/blob/master/agent/plugin-api/src/main/java/org/glowroot/agent/plugin/api/weaving/Shim.java
 */
@Target(METHOD)
@Retention(RUNTIME)
public @interface Bridge {
  /**
   * Java method declaration, without argument names, of the form "returnType name (argumentType1,
   * ... argumentTypeN)", where the types are in plain Java (e.g. "int", "float", "java.util.List",
   * ...). Classes of the java.lang package can be specified by their unqualified name; all other
   * classes names must be fully qualified.
   *
   * @return the method declaration
   */
  String value();
}
