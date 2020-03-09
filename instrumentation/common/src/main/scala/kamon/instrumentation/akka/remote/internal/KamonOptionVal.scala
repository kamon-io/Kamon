package akka

import akka.util.{ OptionVal => AkkaOptionVal }
/**
  * The sole purpose of this object is to provide access to the otherwise internal class [[akka.util.OptionVal]].
  */
object KamonOptionVal {
  type OptionVal[+T >: Null] = AkkaOptionVal[T]
}
