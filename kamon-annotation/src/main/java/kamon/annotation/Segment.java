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
 * A marker annotation to start a new Segment.
 * <p>
 * <p>
 * Given a method like this:
 * <pre><code>
 *     {@literal @}Segment("coolSegmentName", tags="${'my-cool-tag':'my-cool-value'}")
 *     public String coolName(String name) {
 *         return "Hello " + name;
 *     }
 * </code></pre>
 * <p>
 * <p>
 * A new Segment will be created only if in the moment of the method execution exist a TraceContext.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Segment {

    /**
     * @return The Segment's name.
     * <p>
     * Also, the Segment name can be resolved with an EL expression that evaluates to a String:
     * <p>
     * <pre>
     * {@code
     *  class Segment {
     *        private long id;
     *
     *        public long getId() { return id; }
     *
     *        {@literal @}Segment (name = "${'segmentID:' += this.id}")
     *        void segment() {} // create a Segment with name => segmentID:[id]
     *    }
     * }
     * </pre>
     */
    String name();

    /**
     * @return The Segment's category.
     */
    String category();

    /**
     * @return The Segment's library.
     */
    String library();

    /**
     * Tags are a way of adding dimensions to metrics,
     * these are constructed using EL syntax e.g. "${'algorithm':'1','env':'production'}"
     *
     * @return the tags associated to the segment
     */
    String tags() default "";
}
