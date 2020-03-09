/*
 * =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.annotation.api;

import java.lang.annotation.*;

/**
 * Indicates that invocations of the annotated method should be counted using a Kamon Counter. For example, given a
 * method like this:
 *
 * <pre>{@code
 * @Count(name = "coolName", tags="${'my-cool-tag':'my-cool-operationName'}")
 * public String coolName(String name) {
 *   return "Hello " + name;
 * }
 * }</pre>
 *
 * <p>
 * A {@link kamon.metric.Counter Counter} named {@code coolName} will be created and incremented each time the
 * {@code coolName(String)} method is invoked.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Count {

  /**
   * The counter's metric name. It can be provided as a plain String or as an EL expression. For example, the code
   * bellow uses the "id" property of the class in an EL expression to create the counter name.
   *
   * <pre>{@code
   * class Counted  {
   *   private long id;
   *
   *   public long getId() { return id; }
   *
   *   @Count(name = "${'counterID:' += this.id}")
   *   void countedMethod() {
   *     // create a counter with name => counterID:[id]
   *   }
   * }
   * }</pre>
   *
   * @return The counter's name. If no name is provided, a name will be generated using the fully qualified class name
   *         and the method name.
   */
  String name() default "";

  /**
   * An EL expression that generates tags for the counter metric. For example, the expression "${'algorithm':'1','env':'production'}"
   * adds the tags "algorithm=1" and "env=production" to the counter.
   *
   * @return an EL expression that generates tags for the counter metric.
   */
  String tags() default "";
}
