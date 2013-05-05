package akka

import org.aspectj.lang.annotation.{Pointcut, Before, Aspect}
import akka.actor.ActorSystemImpl
import com.typesafe.config.Config
import java.util.concurrent.{TimeUnit, Executors}

@Aspect
class ActorSystemAspect {

  @Pointcut("execution(akka.actor.ActorSystemImpl.new(..))")
  protected def actorSystem:Unit = {}

  @Before("actorSystem() && this(system) && args(name, config, classLoader)")
  def beforeInitialize(system: ActorSystemImpl, name: String, config: Config, classLoader: ClassLoader) {

    val scheduler = Executors.newScheduledThreadPool(1);

    scheduler.scheduleAtFixedRate(new Runnable {
      def run() {
        println("ActorSystemImpl" + system.name)
        println("Thread Factory" + system.threadFactory.name)
        println("Dispatchers" + system.dispatchers.defaultDispatcherConfig.resolve)
        println("Dispatcher" + system.dispatcher.throughput)
      }
    }, 4, 4, TimeUnit.SECONDS)
  }
}
