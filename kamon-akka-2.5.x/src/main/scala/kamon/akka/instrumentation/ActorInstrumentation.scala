package akka.kamon.instrumentation


import akka.actor._
import scala.util.Properties


object ActorCellInstrumentation {
  val (unstartedCellQueueField, unstartedCellLockField, systemMsgQueueField) = {
    val unstartedCellClass = classOf[UnstartedCell]

    val prefix = Properties.versionNumberString.split("\\.").take(2).mkString(".") match {
      case _@ "2.11" ⇒ "akka$actor$UnstartedCell$$"
      case _@ "2.12" ⇒ ""
      case v         ⇒ throw new IllegalStateException(s"Incompatible Scala version: $v")
    }

    val queueField = unstartedCellClass.getDeclaredField(prefix+"queue")
    queueField.setAccessible(true)

    val lockField = unstartedCellClass.getDeclaredField("lock")
    lockField.setAccessible(true)

    val sysQueueField = unstartedCellClass.getDeclaredField(prefix+"sysmsgQueue")
    sysQueueField.setAccessible(true)

    (queueField, lockField, sysQueueField)
  }

}

trait ActorInstrumentationAware {
  def actorInstrumentation: ActorMonitor
  def setActorInstrumentation(ai: ActorMonitor): Unit
}

object ActorInstrumentationAware {
  def apply(): ActorInstrumentationAware = new ActorInstrumentationAware {
    private var _ai: ActorMonitor = _

    def setActorInstrumentation(ai: ActorMonitor): Unit = _ai = ai
    def actorInstrumentation: ActorMonitor = _ai
  }
}
