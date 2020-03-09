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


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Indicates that invocations of the annotated method should be timed using a Kamon Time. If the return type of the
 * annotated method is a Future or CompletionStage, the generated Span will be finished when the Future or
 * CompletionStage completes.
 *
 * For example, given a method like this:
 *
 * <pre>{@code
 * @Time(name = "coolName", tags="""${{'my-cool-tag':'my-cool-operationName'}}""")
 * public String coolName(String name) {
 *   return "Hello " + name;
 * }
 * }</pre>
 *
 * <p>
 * A Timer named {@code coolName} will be created and each time the {@code #coolName(String)} method is invoked, the
 * latency of execution will be recorded on that Time.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Time {

  /**
   * @return The metric name to be used for the Timer. If no name is provided, a name will be generated using the fully
   * qualified class name and the method name.
   */
  String name() default "";

  /**
   * An EL expression that generates tags for the Time metric. For example, the expression "${'algorithm':'1','env':'production'}"
   * adds the tags "algorithm=1" and "env=production" to the timer.
   *
   * @return an EL expression that generates tags for the timer metric.
   */
  String tags() default "";
}
