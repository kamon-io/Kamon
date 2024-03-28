package kamon.instrumentation.apache.cxf.client.util

import org.apache.cxf.message.Message
import org.apache.cxf.phase.Phase.{POST_INVOKE, RECEIVE}
import org.apache.cxf.phase.PhaseInterceptor

import java.util.Collections

class FailingInterceptor extends PhaseInterceptor[Message] {
  override def handleMessage(message: Message): Unit = {
    throw new RuntimeException("Dummy Exception")
  }

  override def getAfter: java.util.Set[String] = Collections.emptySet()

  override def getBefore: java.util.Set[String] = Collections.emptySet()

  override def getId: String = getClass.getName

  override def getPhase: String = RECEIVE

  override def getAdditionalInterceptors: java.util.Collection[PhaseInterceptor[_ <: Message]] = null

  override def handleFault(message: Message): Unit = {}
}
