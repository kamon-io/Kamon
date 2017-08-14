package kamon.context

trait Storage {
  def current(): Context
  def store(context: Context): Storage.Scope
}

object Storage {

  trait Scope {
    def context: Context
    def close(): Unit
  }


  class ThreadLocal extends Storage {
    private val tls = new java.lang.ThreadLocal[Context]() {
      override def initialValue(): Context = Context.Empty
    }

    override def current(): Context =
      tls.get()

    override def store(context: Context): Scope = {
      val newContext = context
      val previousContext = tls.get()
      tls.set(newContext)

      new Scope {
        override def context: Context = newContext
        override def close(): Unit = tls.set(previousContext)
      }
    }
  }

  object ThreadLocal {
    def apply(): ThreadLocal = new ThreadLocal()
  }
}