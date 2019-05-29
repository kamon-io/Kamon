package kamon

import kamon.util.DynamicAccess

import scala.collection.immutable
import scala.reflect.ClassTag

/**
  * Exposes APIs for dynamically creating instances and holds the ClassLoader instance to be used by Kamon when looking
  * up resources and classes.
  */
object ClassLoading {

  @volatile private var _dynamicAccessClassLoader = this.getClass.getClassLoader
  @volatile private var _dynamicAccess = new DynamicAccess(_dynamicAccessClassLoader)

  /**
    * Returns the ClassLoader used by Kamon to load resources and dynamically created instances.
    */
  def classLoader(): ClassLoader =
    _dynamicAccessClassLoader

  /**
    * Changes the ClassLoader used by Kamon to load resources and dynamically created instances.
    */
  def changeClassLoader(classLoader: ClassLoader): Unit = synchronized {
    _dynamicAccessClassLoader = classLoader
    _dynamicAccess = new DynamicAccess(_dynamicAccessClassLoader)
  }

  /**
    * Tries to create an instance of a class with the provided fully-qualified class name and its no-arg constructor.
    */
  private[kamon] def createInstance[T: ClassTag](fqcn: String): T =
    _dynamicAccess.createInstanceFor(fqcn, immutable.Seq.empty)

  /**
    * Tries to create an instance of a class with the provided fully-qualified class name and the provided constructor
    * arguments.
    */
  private[kamon] def createInstance[T: ClassTag](fqcn: String, args: immutable.Seq[(Class[_], AnyRef)]): T =
    _dynamicAccess.createInstanceFor(fqcn, args)

  /**
    * Tries to create an instance of the provided Class with its no-arg constructor.
    */
  private[kamon] def createInstance[T: ClassTag](clazz: Class[_]): T =
    _dynamicAccess.createInstanceFor(clazz, immutable.Seq.empty)

  /**
    * Tries to create an instance of with the provided Class and constructor arguments.
    */
  private[kamon] def createInstance[T: ClassTag](clazz: Class[_], args: immutable.Seq[(Class[_], AnyRef)]): T =
    _dynamicAccess.createInstanceFor(clazz, args)

  /**
    * Tries load a class with the provided fully-qualified class name.
    */
  private[kamon] def resolveClass[T: ClassTag](fqcn: String): Class[_ <: T] =
    _dynamicAccess.getClassFor(fqcn)
}
