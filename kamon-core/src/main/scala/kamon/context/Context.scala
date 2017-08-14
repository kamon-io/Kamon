package kamon.context

class Context private (private[context] val entries: Map[Key[_], Any]) {
  def get[T](key: Key[T]): T =
    entries.get(key).getOrElse(key.emptyValue).asInstanceOf[T]

  def withKey[T](key: Key[T], value: T): Context =
    new Context(entries.updated(key, value))
}

object Context {
  val Empty = new Context(Map.empty)

  def apply(): Context =
    Empty

  def create(): Context =
    Empty

  def apply[T](key: Key[T], value: T): Context =
    new Context(Map(key -> value))

  def create[T](key: Key[T], value: T): Context =
    apply(key, value)
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