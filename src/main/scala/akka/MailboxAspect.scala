package akka

import org.aspectj.lang.annotation.{Pointcut, Before, Aspect}
import java.util.concurrent.{TimeUnit, Executors}

@Aspect
class MailboxAspect {

  @Pointcut("execution(akka.dispatch.Mailbox.new(..))")
  protected def mailboxMonitor:Unit = {}

  @Before("mailboxMonitor() && this(mb)")
  def before(mb: akka.dispatch.Mailbox) : Unit = {
    val scheduler = Executors.newScheduledThreadPool(1);

    scheduler.scheduleAtFixedRate(new Runnable {
      def run() {
        println("Mailbox: "  + mb.actor.self.path)
        println("NOM: " + mb.numberOfMessages)
        println("Messages: " + mb.hasMessages)
        print("Dispatcher throughput: " + mb.dispatcher.throughput)
        println(mb.dispatcher.id)
      }
    }, 6, 4, TimeUnit.SECONDS)
  }
}
