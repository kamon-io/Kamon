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
 * Indicates that concurrent invocations of the annotated method should be tracked with a Kamon Range Sampler. The Range
 * Sampler is incremented when the annotated method starts executing and decremented when it finishes. If the return
 * type of the annotated method is a Future or CompletionStage, the Range Sampler will be decremented when the Future or
 * CompletionStage completes.
 *
 * For example, given a method like this:
 *
 * <pre>{@code
 * @TrackConcurrency(name = "coolName", tags="${'my-cool-tag':'my-cool-operationName'}")
 * public String coolName(String name) {
 *   return "Hello " + name;
 * }
 * }</pre>
 *
 * <p>
 * A {@link kamon.metric.RangeSampler RangeSampler} named {@code coolName} will be incremented every time the annotated
 * method starts executing and decremented every time it finishes executing.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TrackConcurrency {

    /**
     * The Range Sampler's metric name. It can be provided as a plain String or as an EL expression. For example, the
     * code bellow uses the "id" property of the class in an EL expression to create the Range Sampler name.
     *
     * <pre>
     * {@code
     * class RangeSampled  {
     *   private long id;
     *
     *   public long getId() { return id; }
     *
     *   @TrackConcurrency(name = "${'counterID:' += this.id}")
     *   void countedMethod() {} // create a counter with name => counterID:[id]
     * }
     * }
     * </pre>
     *
     * @return The Range Sampler's name. If no name is provided, a name will be generated using the fully qualified
     *         class name and the method name.
     */
    String name() default "";

    /**
     * An EL expression that generates tags for the Range Sampler metric. For example, the expression "${'algorithm':'1','env':'production'}"
     * adds the tags "algorithm=1" and "env=production" to the Range Sampler.
     *
     * @return an EL expression that generates tags for the Range Sampler metric.
     */
    String tags() default "";
}
