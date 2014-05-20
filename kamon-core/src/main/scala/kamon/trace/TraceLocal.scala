package kamon.trace

import scala.collection.concurrent.TrieMap
import kamon.trace.TraceLocal.TraceLocalKey

object TraceLocal {
  trait TraceLocalKey {
    type ValueType
  }

  def store(key: TraceLocalKey)(value: key.ValueType): Unit =
    TraceRecorder.currentContext.map(_.traceLocalStorage.store(key)(value))

  def retrieve(key: TraceLocalKey): Option[key.ValueType] =
    TraceRecorder.currentContext.flatMap(_.traceLocalStorage.retrieve(key))

}

class TraceLocalStorage {
  val underlyingStorage = TrieMap[TraceLocal.TraceLocalKey, Any]()

  def store(key: TraceLocalKey)(value: key.ValueType): Unit = underlyingStorage.put(key, value)

  def retrieve(key: TraceLocalKey): Option[key.ValueType] = underlyingStorage.get(key).map(_.asInstanceOf[key.ValueType])
}
