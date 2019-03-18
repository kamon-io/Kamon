package kamon

import kamon.util.DynamicAccess

import scala.collection.immutable
import scala.reflect.ClassTag
import scala.util.Try

/**
  *   Utilities for creating instances from fully qualified class names.
  */
trait ClassLoading {
  @volatile private var _dynamicAccessClassLoader = this.getClass.getClassLoader
  @volatile private var _dynamicAccess = new DynamicAccess(_dynamicAccessClassLoader)

  def classLoader(): ClassLoader =
    _dynamicAccessClassLoader

  def changeClassLoader(classLoader: ClassLoader): Unit = synchronized {
    _dynamicAccessClassLoader = classLoader
    _dynamicAccess = new DynamicAccess(_dynamicAccessClassLoader)
  }

  def createInstance[T: ClassTag](fqcn: String, args: immutable.Seq[(Class[_], AnyRef)]): Try[T] =
    _dynamicAccess.createInstanceFor(fqcn, args)

  def createInstance[T: ClassTag](clazz: Class[_], args: immutable.Seq[(Class[_], AnyRef)]): Try[T] =
    _dynamicAccess.createInstanceFor(clazz, args)

  def resolveClass[T: ClassTag](fqcn: String): Try[Class[_ <: T]] =
    _dynamicAccess.getClassFor(fqcn)
}
