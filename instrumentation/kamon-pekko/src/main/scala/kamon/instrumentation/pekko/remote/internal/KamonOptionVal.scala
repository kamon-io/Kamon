package org.apache.pekko

import org.apache.pekko.util.{ OptionVal => PekkoOptionVal }
/**
  * The sole purpose of this object is to provide access to the otherwise internal class [[org.apache.pekko.util.OptionVal]].
  */
object KamonOptionVal {
  type OptionVal[+T >: Null] = PekkoOptionVal[T]
}
