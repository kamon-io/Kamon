/*
 * Copyright 2013-2021 The Kamon Project <https://kamon.io>
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

package kamon.instrumentation.logback

import com.typesafe.config.Config
import kamon.Kamon
import kamon.context.Context
import kamon.context.Storage.Scope
import kamon.instrumentation.context.HasContext
import kamon.tag.Tag
import kamon.trace.{Identifier, Span}
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice

import java.util
import scala.collection.JavaConverters._


class LogbackInstrumentation extends InstrumentationBuilder {

  onSubTypesOf("ch.qos.logback.core.spi.DeferredProcessingAware")
    .mixin(classOf[HasContext.MixinWithInitializer])

  onType("ch.qos.logback.core.spi.AppenderAttachableImpl")
    .advise(method("appendLoopOnAppenders"), AppendLoopOnAppendersAdvice)

  onType("ch.qos.logback.classic.util.LogbackMDCAdapter")
    .advise(method("getPropertyMap"), classOf[MdcPropertyMapAdvice])
}

object LogbackInstrumentation {

  @volatile private var _settings = readSettings(Kamon.config())
  Kamon.onReconfigure(newConfig => _settings = readSettings(newConfig))

  def settings(): Settings =
    _settings

  case class Settings(
    propagateContextToMDC: Boolean,
    mdcTraceIdKey: String,
    mdcSpanIdKey: String,
    mdcSpanOperationNameKey: String,
    mdcSourceThreadKey: String,
    mdcCopyTags: Boolean,
    mdcCopyKeys: Seq[String]
  )

  private def readSettings(config: Config): Settings = {
    val logbackConfig = config.getConfig("kamon.instrumentation.logback")

    Settings(
      logbackConfig.getBoolean("mdc.copy.enabled"),
      logbackConfig.getString("mdc.trace-id-key"),
      logbackConfig.getString("mdc.span-id-key"),
      logbackConfig.getString("mdc.span-operation-name-key"),
      logbackConfig.getString("mdc.source-thread-key"),
      logbackConfig.getBoolean("mdc.copy.tags"),
      logbackConfig.getStringList("mdc.copy.entries").asScala.toSeq
    )
  }
}

object AppendLoopOnAppendersAdvice {

  @Advice.OnMethodEnter
  def enter(@Advice.Argument(0) event: Any): Scope =
    Kamon.storeContext(event.asInstanceOf[HasContext].context)

  @Advice.OnMethodExit
  def exit(@Advice.Enter scope: Scope): Unit =
    scope.close()
}

object ContextToMdcPropertyMapAppender {

  def appendContext(mdc: java.util.Map[String, String]): java.util.Map[String, String] = {
    val settings = LogbackInstrumentation.settings()

    if (settings.propagateContextToMDC) {
      val currentContext = Kamon.currentContext()
      val span = currentContext.get(Span.Key)
      val mdcWithKamonContext = {
        if(mdc == null)
          new util.HashMap[String, String]()
        else
          new util.HashMap[String, String](mdc)
      }

      if (span.trace.id != Identifier.Empty) {
        mdcWithKamonContext.put(settings.mdcTraceIdKey, span.trace.id.string)
        mdcWithKamonContext.put(settings.mdcSpanIdKey, span.id.string)
        mdcWithKamonContext.put(settings.mdcSpanOperationNameKey, span.operationName())
      }

      mdcWithKamonContext.put(settings.mdcSourceThreadKey, Thread.currentThread().getName)

      if (settings.mdcCopyTags) {
        currentContext.tags.iterator().foreach(t => {
          mdcWithKamonContext.put(t.key, Tag.unwrapValue(t).toString)
        })
      }

      settings.mdcCopyKeys.foreach { key =>
        currentContext.get(Context.key[Any](key, "")) match {
          case Some(value) if value.toString.nonEmpty => mdcWithKamonContext.put(key, value.toString)
          case keyValue if keyValue != null && keyValue.toString.nonEmpty => mdcWithKamonContext.put(key, keyValue.toString)
          case _ => // Just ignore the nulls and empty strings
        }
      }

      mdcWithKamonContext
    } else {
      mdc
    }
  }
}
