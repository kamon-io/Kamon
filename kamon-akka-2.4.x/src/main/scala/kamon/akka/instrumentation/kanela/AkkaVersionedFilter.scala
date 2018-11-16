package kamon.akka.instrumentation.kanela

import kanela.agent.api.instrumentation.InstrumentationDescription
import kanela.agent.api.instrumentation.classloader.ClassLoaderRefiner
import kanela.agent.scala.KanelaInstrumentation

trait AkkaVersionedFilter {
  self: KanelaInstrumentation =>

  def filterAkkaVersion(builder: InstrumentationDescription.Builder): InstrumentationDescription.Builder = {
    builder.withClassLoaderRefiner(ClassLoaderRefiner.mustContains("akka.event.ActorClassification"))
  }

}
