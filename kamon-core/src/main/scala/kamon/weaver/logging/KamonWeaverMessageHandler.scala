/*
 * =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
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

package kamon.weaver.logging

import org.aspectj.bridge.{ IMessage, IMessageHandler }
import com.typesafe.config.ConfigFactory
import java.util.logging.Logger

/**
 *  Implementation of AspectJ's IMessageHandler interface that routes AspectJ weaving messages and controls them through kamon configuration.
 */
class KamonWeaverMessageHandler extends IMessageHandler {
  import IMessage._

  private val log = Logger.getLogger("AspectJ Weaver")
  private val conf = ConfigFactory.load().getConfig("kamon.weaver")

  private val isVerbose = conf.getBoolean("verbose")
  private val isDebug = conf.getBoolean("debug")
  private val showWeaveInfo = conf.getBoolean("showWeaveInfo")
  private val showWarn = conf.getBoolean("showWarn")

  def handleMessage(message: IMessage) = message.getKind match {
    case WEAVEINFO if showWeaveInfo ⇒ showMessage(message)
    case DEBUG if isDebug           ⇒ showMessage(message)
    case WARNING if showWarn        ⇒ showMessage(message)
    case DEBUG if isDebug           ⇒ showMessage(message)
    case INFO if isVerbose          ⇒ showMessage(message)
    case ERROR                      ⇒ showErrorMessage(message)
    case _                          ⇒ false
  }

  def isIgnoring(kind: IMessage.Kind): Boolean = false // We want to see everything.
  def dontIgnore(kind: IMessage.Kind) = {}
  def ignore(kind: IMessage.Kind) = {}

  private def showMessage(msg: IMessage): Boolean = {
    log.info(msg.getMessage)
    true
  }

  private def showErrorMessage(msg: IMessage): Boolean = {
    log.severe(msg.getMessage)
    true
  }
}

