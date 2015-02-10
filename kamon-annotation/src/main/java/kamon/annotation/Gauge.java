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
import kamon.metric.instrument.Gauge.CurrentValueCollector;

/**
 * A marker annotation to define a method as a gauge.
 *
 * <p/>
 * Given a method like this:
 * <pre><code>
 *     {@literal @}Gauge(name = "coolName", tags="""${{'my-cool-tag':'my-cool-value'}}""")
 *     public Integer coolName() {
 *         return someComputation();
 *     }
 * </code></pre>
 * <p/>
 *
 * A gauge for the defining method with the name {@code coolName} will be created which uses the
 * annotated method's return as its value.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Gauge {
    /**
     * @return The gauge's name.
     */
    String name();

    /**
     * Tags are a way of adding dimensions to metrics,
     * these are constructed using EL syntax e.g. """${{'algorithm':'1','env':'production'}}"""
     *
     * @return the tags associated to the gauge
     */
    String tags() default "";

    /**
     * Specifies the gauge value collector @see CurrentValueCollector
     *
     * @return the current value collector
     */
    Class<? extends CurrentValueCollector> collector() default DefaultValueCollector.class;
}
