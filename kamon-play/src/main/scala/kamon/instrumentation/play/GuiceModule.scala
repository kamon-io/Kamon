package kamon.instrumentation.play

import javax.inject._
import kamon.Kamon
import play.api.inject.{ApplicationLifecycle, Binding, Module}
import play.api.{Configuration, Environment, Logger, Mode}

import scala.concurrent.Future

class GuiceModule extends Module {
  def bindings(environment: Environment, configuration: Configuration): Seq[Binding[GuiceModule.KamonLoader]] = {
    Seq(bind[GuiceModule.KamonLoader].toSelf.eagerly())
  }
}

object GuiceModule {

  @Singleton
  class KamonLoader @Inject() (lifecycle: ApplicationLifecycle, environment: Environment, configuration: Configuration) {
    Logger(classOf[KamonLoader]).info("Reconfiguring Kamon with Play's Config")
    Kamon.reconfigure(configuration.underlying)
    Kamon.loadModules()

    lifecycle.addStopHook { () =>
      if(environment.mode != Mode.Dev)
        Future.successful(Kamon.stopModules())
      else
        Future.successful(())
    }
  }
}
