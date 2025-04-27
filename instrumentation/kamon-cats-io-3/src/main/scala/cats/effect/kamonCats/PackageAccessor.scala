package cats.effect.kamonCats

import org.slf4j.LoggerFactory

/**
 * Utility class to make accessing some internals from Kamon, more accessible.
 *
 */
object PackageAccessor {

  private val LOG = LoggerFactory.getLogger(getClass)

  /**
   * This uses reflection to get the objectState, which acts like a stack
   * of effects so we can determine something about the lineage of a particular fiber.
   *
   * @param fiber A runnable or IOFiber
   * @return
   */
  def fiberObjectStackBuffer(fiber: Any): Array[AnyRef] = {
    try {
      val field = fiber.getClass.getDeclaredField("objectState")
      field.setAccessible(true)
      field.get(fiber).asInstanceOf[cats.effect.ArrayStack[AnyRef]].unsafeBuffer()
    } catch {
      case _: Exception =>
        if (LOG.isWarnEnabled)
          LOG.warn("Unable to get the object stack buffer.")
        Array.empty
    }

  }

  /** This frankly kinda isn't great, but I couldn't figure out how to do this  */
  def isDispatcherWorker(obj: AnyRef): Boolean = {
    if (obj != null)
      obj.getClass.getName.contains("Dispatcher$Worker")
    else false
  }

}
