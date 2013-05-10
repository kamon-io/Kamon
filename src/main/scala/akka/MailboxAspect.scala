package akka

import org.aspectj.lang.annotation._
import scala.concurrent.duration._
import com.newrelic.api.agent.NewRelic

@Aspect("perthis(mailboxMonitor())")
class MailboxAspect extends ActorSystemHolder {

  @Pointcut("execution(akka.dispatch.Mailbox.new(..)) && !within(MailboxAspect)")
  protected def mailboxMonitor():Unit = {}

  @Before("mailboxMonitor() && this(mb)")
  def before(mb: akka.dispatch.Mailbox) : Unit = {
    actorSystem.scheduler.schedule(5 seconds, 6 second, new Runnable {
      def run() {

        val actorName = mb.actor.self.path.toString

        println(s"Sending metrics to Newrelic MailBoxMonitor -> ${actorName}")


        NewRelic.recordMetric(s"${actorName}:Mailbox:NumberOfMessages",mb.numberOfMessages)
        NewRelic.recordMetric(s"${actorName}:Mailbox:MailboxDispatcherThroughput",mb.dispatcher.throughput)

        NewRelic.addCustomParameter(s"${actorName}:Mailbox:Status", mb.hasMessages.toString)
        NewRelic.addCustomParameter(s"${actorName}:Mailbox:HasMessages", mb.hasMessages.toString)
      }
    })
  }
}