package kamon

import akka.actor.{ActorSystem, ExtensionId}
import java.util.concurrent.ConcurrentHashMap

object AkkaExtensionSwap {
  def swap(system: ActorSystem, key: ExtensionId[_], value: Kamon.Extension): Unit = {
    val extensionsField = system.getClass.getDeclaredField("extensions")
    extensionsField.setAccessible(true)

    val extensions = extensionsField.get(system).asInstanceOf[ConcurrentHashMap[ExtensionId[_], AnyRef]]
    extensions.put(key, value)
  }
}
