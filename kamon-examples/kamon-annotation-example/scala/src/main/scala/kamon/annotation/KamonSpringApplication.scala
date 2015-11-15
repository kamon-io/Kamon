package kamon.annotation

import kamon.Kamon
import org.springframework.boot.SpringApplication

object KamonSpringApplication extends App {
  Kamon.start()
  
  SpringApplication.run(classOf[KamonController])
}
