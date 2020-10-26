/*
 * Copyright 2013-2020 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kamon.instrumentation.context

import kanela.agent.libs.net.bytebuddy.asm.Advice

/**
  * Advise that copies the current System.nanoTime into a HasTimestamp instance when the advised method starts
  * executing.
  */
object CaptureCurrentTimestampOnEnter {

  @Advice.OnMethodEnter
  def enter(@Advice.This hasTimestamp: HasTimestamp): Unit =
    hasTimestamp.setTimestamp(System.nanoTime())

}

/**
  * Advise that copies the current System.nanoTime into a HasTimestamp instance when the advised method finishes
  * executing.
  */
object CaptureCurrentTimestampOnExit {

  @Advice.OnMethodExit
  def exit(@Advice.This hasTimestamp: HasTimestamp): Unit =
    hasTimestamp.setTimestamp(System.nanoTime())

}
