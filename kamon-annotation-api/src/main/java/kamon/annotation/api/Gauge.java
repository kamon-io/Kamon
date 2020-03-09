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
 * A marker annotation to define a method as a Gauge.
 * <p>
 * <p>
 * Given a method like this:
 * <pre><code>
 *     {@literal @}Gauge(name = "coolName", tags="${'my-cool-tag':'my-cool-operationName'}")
 *     public (Long|Double|Float|Integer) coolName() {
 *         return 100L;
 *     }
 * </code></pre>
 * <p>
 * <p>
 * A {@link kamon.metric.Gauge Gauge} for the defining method with the name {@code coolName} will be created and each time the
 * {@code #coolName(String)} method is invoked, the gauge will be set the returned value.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Gauge {

    /**
     * @return The gauge's name.
     * <p>
     * Also, the Metric name can be resolved with an EL expression that evaluates to a String:
     * <p>
     * <pre>
     * {@code
     *  class Gauged  {
     *        private long id;
     *
     *        public long getId() { return id; }
     *
     *        {@literal @}Gauge (name = "${'counterID:' += this.id}")
     *        void gaugedMethod() {} // create a gauges with name => gaugeID:[id]
     *    }
     * }
     * </pre>
     */
    String name() default "";

    /**
     * Tags are a way of adding dimensions to metrics,
     * these are constructed using EL syntax e.g. "${'algorithm':'1','env':'production'}"
     *
     * @return the tags associated to the counter
     */
    String tags() default "";
}
