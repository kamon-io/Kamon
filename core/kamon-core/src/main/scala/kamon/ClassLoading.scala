/*
 * Copyright 2013-2020 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kamon

import kamon.util.DynamicAccess

import scala.collection.immutable
import scala.reflect.ClassTag

/**
  * Exposes APIs for dynamically creating instances and holds the ClassLoader instance to be used by Kamon when looking
  * up resources and classes.
  */
sealed trait ClassLoading {

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
  def createInstance[T: ClassTag](fqcn: String): T =
    _dynamicAccess.createInstanceFor(fqcn, immutable.Seq.empty)

  /**
    * Tries to create an instance of a class with the provided fully-qualified class name and the provided constructor
    * arguments.
    */
  def createInstance[T: ClassTag](fqcn: String, args: immutable.Seq[(Class[_], AnyRef)]): T =
    _dynamicAccess.createInstanceFor(fqcn, args)

  /**
    * Tries to create an instance of the provided Class with its no-arg constructor.
    */
  def createInstance[T: ClassTag](clazz: Class[_]): T =
    _dynamicAccess.createInstanceFor(clazz, immutable.Seq.empty)

  /**
    * Tries to create an instance of with the provided Class and constructor arguments.
    */
  def createInstance[T: ClassTag](clazz: Class[_], args: immutable.Seq[(Class[_], AnyRef)]): T =
    _dynamicAccess.createInstanceFor(clazz, args)

  /**
    * Tries load a class with the provided fully-qualified class name.
    */
  def resolveClass[T: ClassTag](fqcn: String): Class[_ <: T] =
    _dynamicAccess.getClassFor(fqcn)
}

object ClassLoading extends ClassLoading
