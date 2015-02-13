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
 * A marker annotation to define a method as a counter.
 *
 * <p/>
 * Given a method like this:
 * <pre><code>
 *     {@literal @}Count(name = "coolName", tags="""${{'my-cool-tag':'my-cool-value'}}""", type=CounterType.MinMaxCounter)
 *     public String coolName(String name) {
 *         return "Hello " + name;
 *     }
 * </code></pre>
 * <p/>
 *
 * A counter for the defining method with the name {@code coolName} will be created and each time the
 * {@code #coolName(String)} method is invoked, the counter will be incremented.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Count {

    /**
     * @return The counter's name.
     */
    String name();

    /**
     * Tags are a way of adding dimensions to metrics,
     * these are constructed using EL syntax e.g. """${{'algorithm':'1','env':'production'}}"""
     *
     * @return the tags associated to the counter
     */
    String tags() default "";
}
