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


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Indicates that any Spans created within the scope of the annotated method should have the customizations provided by
 * this annotation. Currently the only supported customization is to change the operation name. For example, given a
 * method like this:
 *
 * <pre>{@code
 * @CustomizeInnerSpan(operationName = "coolName")
 * public String callDatabase(String query) {
 *   return Db.executeQuery(query);
 * }
 * }</pre>
 * <p>
 * <p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CustomizeInnerSpan {

  /**
   * @return The operationName for any Span created within the Scope of the annotated method.
   */
  String operationName();
}
