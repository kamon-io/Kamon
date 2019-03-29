package kamon

import scala.reflect.ClassTag

package object testkit {

  /**
    * Retrieves a member from the target object using reflection.
    */
  def getField[T, R](target: T, fieldName: String)(implicit classTag: ClassTag[T]): R = {
    val field = classTag.runtimeClass.getDeclaredFields.find(_.getName.contains(fieldName)).get
    field.setAccessible(true)
    field.get(target).asInstanceOf[R]
  }

  /**
    * Retrieves a member from the target object using reflection.
    */
  def getFieldFromClass[T](target: Any, className: String, fieldName: String): T = {
    val field = Class.forName(className).getDeclaredFields.find(_.getName.contains(fieldName)).get
    field.setAccessible(true)
    field.get(target).asInstanceOf[T]
  }

  /**
    * Invokes a method on the target object using reflection.
    */
  def invoke[T, R](target: Any, fieldName: String, parameters: (Class[_], AnyRef)*)(implicit classTag: ClassTag[T]): R = {
    val parameterClasses = parameters.map(_._1)
    val parameterInstances = parameters.map(_._2)
    val method = classTag.runtimeClass.getDeclaredMethod(fieldName, parameterClasses: _*)
    method.setAccessible(true)
    method.invoke(target, parameterInstances: _*).asInstanceOf[R]
  }
}
