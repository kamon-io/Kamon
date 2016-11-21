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
* A marker annotation for enable the Kamon instrumentation.
* <p/>
* The AspectJ Weaver will scan all the declared methods of the annotated class that are annotated with
* some Kamon annotations, then create and register the corresponding instruments instances and finally weave
* its aspects around these methods, so that at runtime, these instruments instances get called according
* to the Kamon annotations specification.
 */

@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface EnableKamon {}
