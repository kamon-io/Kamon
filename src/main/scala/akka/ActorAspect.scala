package akka

import actor.ActorCell
import org.aspectj.lang.annotation.{After, Around, Pointcut, Aspect}
import org.aspectj.lang.ProceedingJoinPoint
import kamon.metric.Metrics.{ metricsRegistry => meterRegistry }
import com.codahale.metrics.Meter
import kamon.metric.MetricsUtils._

@Aspect("perthis(actorCellCreation(*))")
class ActorAspect {

  /**
   *  Aspect members
   */

  private val actorMeter:Meter = new Meter

  /**
   *  Pointcuts
   */
  @Pointcut("execution(akka.actor.ActorCell+.new(..)) && this(actor)")
  def actorCellCreation(actor:ActorCell):Unit = {}

  @Pointcut("execution(* akka.actor.ActorCell+.receiveMessage(..))")
  def actorReceive():Unit = {}

  /**
   *  Advices
   */
   @After("actorCellCreation(actor)")
   def afterCellCreation(actor:ActorCell):Unit ={
      val actorName:String = actor.self.path.toString

      meterRegistry.register(s"meter-for-${actorName}", actorMeter)
   }

   @Around("actorReceive()")
   def around(pjp: ProceedingJoinPoint) = {
     import pjp._

     markMeter(actorMeter) {
        proceed
     }
   }
 }