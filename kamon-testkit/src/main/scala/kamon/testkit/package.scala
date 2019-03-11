package kamon

import scala.reflect.ClassTag

package object testkit {

  /**
    * Retrieves a member from the target object using reflection.
    */
  def getField[T, R](target: T, fieldName: String)(implicit classTag: ClassTag[T]): R = {
    println("FIELDS: \n" + classTag.runtimeClass)
    println("FIELDS: \n" + classTag.runtimeClass.getFields.mkString("\n"))
    val toFinishedSpanMethod = classTag.runtimeClass.getFields.find(_.getName.contains(fieldName)).get
    toFinishedSpanMethod.setAccessible(true)
    toFinishedSpanMethod.get(target).asInstanceOf[R]
  }

  /**
    * Retrieves a member from the target object using reflection.
    */
  def getFieldFromClass[T](target: Any, className: String, fieldName: String): T = {
    val toFinishedSpanMethod = Class.forName(className).getDeclaredFields.find(_.getName.contains(fieldName)).get
    toFinishedSpanMethod.setAccessible(true)
    toFinishedSpanMethod.get(target).asInstanceOf[T]
  }
}
