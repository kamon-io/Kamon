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
 * A marker annotation to define a method as a Histogram.
 * <p>
 * <p>
 * Given a method like this:
 * <pre><code>
 *     {@literal @}0Histogram(name = "coolName", tags="${'my-cool-tag':'my-cool-value'}")
 *     public (Long|Double|Float|Integer) coolName() {
 *         return someComputation();
 *     }
 * </code></pre>
 * <p>
 * <p>
 * A {@link kamon.metric.instrument.Histogram Histogram} for the defining method with the name {@code coolName}  will be created which uses the
 * annotated method's return as its value.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Histogram {

    /**
     * @return The histogram's name.
     * <p>
     * Also, the Metric name can be resolved with an EL expression that evaluates to a String:
     * <p>
     * <pre>
     * {@code
     * class ClassWithHistogram  {
     *        private long id;
     *
     *        public long getId() { return id; }
     *
     *        {@literal @}Histogram (name = "${'histoID:' += this.id}")
     *        void countedMethod() {} // create a histogram with name => histoID:[id]
     *   }
     * }
     * </pre>
     */
    String name();

    /**
     * The lowest value that can be discerned (distinguished from 0) by the histogram.Must be a positive integer that
     * is >= 1. May be internally rounded down to nearest power of 2.
     */
    long lowestDiscernibleValue() default 1;


    /**
     * The highest value to be tracked by the histogram. Must be a positive integer that is >= (2 * lowestDiscernibleValue).
     * Must not be larger than (Long.MAX_VALUE/2).
     */
    long highestTrackableValue() default 3600000000000L;

    /**
     * The number of significant decimal digits to which the histogram will maintain value resolution and separation.
     * Must be a non-negative integer between 1 and 3.
     */
    int precision() default 2;


    /**
     * Tags are a way of adding dimensions to metrics,
     * these are constructed using EL syntax e.g. "${'algorithm':'1','env':'production'}"
     *
     * @return the tags associated to the histogram
     */
    String tags() default "";
}