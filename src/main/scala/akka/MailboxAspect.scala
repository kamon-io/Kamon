package akka

import org.aspectj.lang.annotation._

@Aspect("perthis(mailboxMonitor())")
class MailboxAspect {
  println("Created MailboxAspect")

  @Pointcut("execution(akka.dispatch.Mailbox.new(..)) && !within(MailboxAspect)")
  protected def mailboxMonitor():Unit = {}

  @After("mailboxMonitor() && this(mb)")
  def afterInitialization(mb: akka.dispatch.Mailbox) : Unit = {
    Tracer.collectMailBox(mb)
  }
}