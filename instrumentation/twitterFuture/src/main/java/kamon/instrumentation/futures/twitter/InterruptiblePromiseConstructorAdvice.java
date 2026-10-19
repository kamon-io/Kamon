/*
 *  ==========================================================================================
 *  Copyright © 2013-2022 The Kamon Project <https://kamon.io/>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 *  except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the
 *  License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied. See the License for the specific language governing permissions
 *  and limitations under the License.
 *  ==========================================================================================
 */

package kamon.instrumentation.futures.twitter;

import kamon.Kamon;
import kanela.agent.libs.net.bytebuddy.asm.Advice;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;

public class InterruptiblePromiseConstructorAdvice {

  @Advice.OnMethodExit
  public static void onConstructorExit(@Advice.FieldValue(value = "handler", readOnly = false) PartialFunction<Throwable, BoxedUnit> handler) {
    if(!(handler instanceof InterruptibleHandlerWithContext)) {
      handler = new InterruptibleHandlerWithContext(Kamon.currentContext(), handler);
    }
  }

}
