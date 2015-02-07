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

package kamon.annotation;

import java.lang.annotation.*;

/**
 * A marker annotation to start a new Trace.
 * <p>
 * <p>
 * Given a method like this:
 * <pre><code>
 *     {@literal @}Trace("coolTraceName", tags="${'my-cool-tag':'my-cool-value'}")
 *     public String coolName(String name) {
 *         return "Hello " + name;
 *     }
 * </code></pre>
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
     * @return The Trace's name.
     * <p>
     * Also, the Trace name can be resolved with an EL expression that evaluates to a String:
     * <p>
     * <pre>
     * {@code
     *  class Traced  {
     *        private long id;
     *
     *        public long getId() { return id; }
     *
     *        {@literal @}Trace (name = "${'traceID:' += this.id}")
     *        void countedMethod() {} // create a Trace with name => traceID:[id]
     *    }
     * }
     * </pre>
     */
    String value();

    /**
     * Tags are a way of adding dimensions to metrics,
     * these are constructed using EL syntax e.g. "${'algorithm':'1','env':'production'}"
     *
     * @return the tags associated to the trace
     */
    String tags() default "";
}
