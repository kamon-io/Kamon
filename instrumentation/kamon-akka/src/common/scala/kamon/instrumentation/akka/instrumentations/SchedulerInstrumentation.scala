package kamon.instrumentation.akka.instrumentations

import kanela.agent.api.instrumentation.InstrumentationBuilder

class SchedulerInstrumentation extends InstrumentationBuilder {

  /**
    * Captures the current context when calling `scheduler.scheduleOnce` and restores it when the submitted runnable
    * runs. This ensures that certain Akka patterns like retry and after work as expected.
    */
  onSubTypesOf("akka.actor.Scheduler")
    .advise(method("scheduleOnce").and(withArgument(1, classOf[Runnable])), classOf[SchedulerRunnableAdvice])
}
