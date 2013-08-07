package kamon.instrumentation

import com.codahale.metrics.{ExponentiallyDecayingReservoir, Histogram}
import org.aspectj.lang.annotation.{After, Pointcut, DeclareMixin, Aspect}

class ActorCage(val name: String, val size: Int) {

  def doIt: Unit = println("name")
}

trait CageMonitoring {
  def histogram: Histogram
  def count(value: Int): Unit
}

class CageMonitoringImp extends CageMonitoring{
  final val histogram = new Histogram(new ExponentiallyDecayingReservoir())

  def count(value: Int) = histogram.update(value)

}


@Aspect
class InceptionAspect {

  @DeclareMixin("kamon.instrumentation.ActorCage")
  def mixin: CageMonitoring = new CageMonitoringImp


  @Pointcut("execution(* kamon.instrumentation.ActorCage.doIt()) && target(actorCage)")
  def theActorCageDidIt(actorCage: CageMonitoring) = {}

  @After("theActorCageDidIt(actorCage)")
  def afterDoingIt(actorCage: CageMonitoring) = {
    actorCage.count(1)
    actorCage.histogram.getSnapshot.dump(System.out)
  }



}


object Runner extends App {
  val cage = new ActorCage("ivan", 10)

  cage.doIt
}
