package kamon.context

class Context private (private val keys: Map[Key[_], Any]) {
  def get[T](key: Key[T]): T =
    keys.get(key).getOrElse(key.emptyValue).asInstanceOf[T]

  def withKey[T](key: Key[T], value: T): Context =
    new Context(keys.updated(key, value))
}

object Context {
  val Empty = new Context(Map.empty)

  def apply(): Context = Empty
  def create(): Context = Empty
}


trait Key[T] {
  def name: String
  def emptyValue: T
  def broadcast: Boolean
}

object Key {

  def local[T](name: String, emptyValue: T): Key[T] =
    new Default[T](name, emptyValue, false)

  def broadcast[T](name: String, emptyValue: T): Key[T] =
    new Default[T](name, emptyValue, true)


  private class Default[T](val name: String, val emptyValue: T, val broadcast: Boolean) extends Key[T] {
    override def hashCode(): Int =
      name.hashCode

    override def equals(that: Any): Boolean =
      that.isInstanceOf[Default[_]] && that.asInstanceOf[Default[_]].name == this.name
  }
}

trait Storage {
  def current(): Context
  def store(context: Context): Scope

  trait Scope {
    def context: Context
    def close(): Unit
  }
}

object Storage {

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
}

trait KeyCodec[T] {
  def encode(context: Context): T
  def decode(carrier: T, context: Context): Context
}

/*
object Example {
  // this is defined somewhere statically, only once.
  val User = Key.local[Option[User]]("user", None)
  val Client = Key.local[Option[User]]("client", null)
  val Span = Key.broadcast[Span]("span", EmptySpan)
  val storage = Kamon.contextStorage // or something similar.

  storage.get(Span) // returns a Span instance or EmptySpan.
  storage.get(User) // Returns Option[User] or None if not set.
  storage.get(Client) // Returns Option[Client] or null if not set.

  // Context Propagation works the very same way as before.

  val scope = storage.store(context)
  // do something here
  scope.close()

  // Configuration for codecs would be handled sort of like this:

  //  kamon.context.propagation {
  //    http-header-codecs {
  //      "span" = kamon.trace.propagation.B3
  //    }
  //
  //    binary-codecs {
  //      "span" = kamon.trace.propagation.Binary
  //    }
  //  }





}*/



/*





class Context(private val keys: Map[Key[_], Any]) {
  




}

object Context {


}

sealed trait Key[T] {
  def name: String
}

object Key {

  def local[T](name: String): Key[T] = Local(name)

  case class Local[T](name: String) extends Key[T]
}*/
