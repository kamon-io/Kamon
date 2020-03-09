/*
 * =========================================================================================
 * Copyright © 2013-2018 the kamon project <http://kamon.io/>
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
 * Indicates a Span should be created to represent invocations of the annotated method. If there is already a Span in
 * the current Context, the new Span will be created as a child of that Span, otherwise a new Trace will be started.
 *
 * The generated Span be automatically finished when the annotated method returns. If the return type of the annotated
 * method is a Future or a CompletionStage, the generated Span will be finished when the Future or CompletionStage
 * completes.
 *
 * <p>
 * For example, given a method like this:
 * <pre>{@code
 * @Trace(operationName = "coolTraceName", tags="${'my-cool-tag':'my-cool-operationName'}")
 * public String coolName(String name) {
 *   return "Hello " + name;
 * }
 * }</pre>
 * <p>
 * <p>
 * A new Trace will be created for the defining method with the name each time the
 * {@code #coolName(String)} method is invoked.
 */

@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Trace {

  /**
   * @return The operation name to be used for the newly created Span. If no name is provided, a name will be
   * generated using the fully qualified class name and the method name.
   */
  String operationName() default "";

  /**
   * An EL expression that contains tags for the generated Span. For example, the "${'algorithm':'1','env':'production'}"
   * adds the tags "algorithm=1" and "env=production" to the Span.
   *
   * @return an EL expression that generates tags for the counter metric.
   */
  String tags() default "";

  /**
   * @return the Span Kind for the newly created Span.
   */
  SpanKind kind() default SpanKind.Internal;

  /**
   * @return the application component that is generated the Span.
   */
  String component() default "annotation";

  /**
   * Describes the kind of operation represented by a Span. This directly matches the Span.Kind values in Kamon.
   */
  enum SpanKind {
    Server,
    Client,
    Producer,
    Consumer,
    Internal,
    Unknown
  }
}
