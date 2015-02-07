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

@EnableKamon
public class AnnotatedJavaClass {

    public static String ID = "10";

    @Trace("trace")
    public static void trace() {}

    @Trace("trace-with-segment")
    public static void segment() {
        inner(); // method annotated with @Segment
    }

    @Trace("trace-with-segment-el")
    public static void segmentWithEL() {
        innerWithEL(); // method annotated with @Segment
    }

    @Count(name = "count")
    public static void count() {}

    @Count(name = "${'count:' += AnnotatedJavaClass.ID}", tags = "${'counter':'1', 'env':'prod'}")
    public static void countWithEL() {}

    @MinMaxCount(name = "minMax")
    public static void countMinMax() {}

    @MinMaxCount(name = "#{'minMax:' += AnnotatedJavaClass.ID}", tags = "#{'minMax':'1', 'env':'dev'}")
    public static void countMinMaxWithEL() {}

    @Time(name = "time")
    public static void time() {}

    @Time(name = "${'time:' += AnnotatedJavaClass.ID}", tags = "${'slow-service':'service', 'env':'prod'}")
    public static void timeWithEL() {}

    @Histogram(name = "histogram")
    public static long histogram(Long value) { return value; }

    @Histogram(name = "#{'histogram:' += AnnotatedJavaClass.ID}", tags = "${'histogram':'hdr', 'env':'prod'}")
    public static long histogramWithEL(Long value) { return value;}

    @Segment(name = "inner-segment", category = "inner", library = "segment")
    private static void inner() {}

    @Segment(name = "#{'inner-segment:' += AnnotatedJavaClass.ID}", category = "segments", library = "segment")
    private static void innerWithEL() {}
}