package kamon.instrumentation.akka.akka25.mixin

import kamon.context.Context

trait ContextContainer  {
  def setContext(context: Context)
  def context: Context
}

