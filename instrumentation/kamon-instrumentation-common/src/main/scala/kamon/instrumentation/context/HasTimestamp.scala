package kamon.instrumentation.context

import kanela.agent.api.instrumentation.mixin.Initializer

/**
  * Mixin that exposes access to a timestamp (from the monotonic clock powering System.nanoTime) captured by an
  * instrumented instance. The interface exposes means of getting and updating a timestamp, but it does not prescribe
  * any ordering or thread safety guarantees, please refer to the available implementations for more details.
  */
trait HasTimestamp {

  /**
    * Returns the timestamp stored in the instrumented instance.
    */
  def timestamp: Long

  /**
    * Updates the timestamp stored in the instrumented instance
    */
  def setTimestamp(timestamp: Long): Unit

}

object HasTimestamp {

  /**
    * HasTimestamp implementation that keeps the timestamp in a mutable field.
    */
  class Mixin extends HasTimestamp {

    // NOTE: It doesn't really matter if we initialize this member here because the initialization code is not copied
    //       to the instrumented classes' constructor. The only way to ensure that a value is assigned to this member is
    //       to use the HasTimestamp.MixinWithInitializer variant or to apply additional instrumentation that assigns the
    //       right timestamp instance using the setTimestamp method.
    private var _timestamp: Long = 0L

    override def timestamp: Long =
      _timestamp

    override def setTimestamp(timestamp: Long): Unit =
      _timestamp = timestamp
  }

  /**
    * HasTimestamp implementation that keeps the timestamp in a volatile field.
    */
  class VolatileMixin extends HasTimestamp {

    // NOTE: It doesn't really matter if we initialize this member here because the initialization code is not copied
    //       to the instrumented classes' constructor. The only way to ensure that a value is assigned to this member is
    //       to use the HasTimestamp.MixinWithInitializer variant or to apply additional instrumentation that assigns the
    //       right timestamp instance using the setTimestamp method.
    @volatile private var _timestamp: Long = 0L

    override def timestamp: Long =
      _timestamp

    override def setTimestamp(timestamp: Long): Unit =
      _timestamp = timestamp
  }


  /**
    * HasTimestamp implementation that keeps the timestamp in a mutable field and initializes it with the result of
    * calling System.nanoTime() when the instrumented instance is initialized.
    */
  class MixinWithInitializer extends HasTimestamp {
    private var _timestamp: Long = 0L

    override def timestamp: Long =
      _timestamp

    override def setTimestamp(timestamp: Long): Unit =
      _timestamp = timestamp

    @Initializer
    def initialize(): Unit =
      setTimestamp(System.nanoTime())
  }

  /**
    * HasTimestamp implementation that keeps the timestamp in a volatile field and initializes it with the result of
    * calling System.nanoTime() when the instrumented instance is initialized.
    */
  class VolatileMixinWithInitializer extends HasTimestamp {
    @volatile private var _timestamp: Long = 0L

    override def timestamp: Long =
      _timestamp

    override def setTimestamp(timestamp: Long): Unit =
      _timestamp = timestamp

    @Initializer
    def initialize(): Unit =
      setTimestamp(System.nanoTime())
  }
}