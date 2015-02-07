package kamon.annotation.instrumentation

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory

object AnnotationBla {
  val system: ActorSystem = ActorSystem("annotations-spec", ConfigFactory.parseString(
    """
      |kamon.metrics {
      |  default-collection-context-buffer-size = 100
      |}
    """.stripMargin))
}

