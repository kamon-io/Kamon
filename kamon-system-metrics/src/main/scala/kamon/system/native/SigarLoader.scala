/*
 * =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.system.native

import java.io._
import java.util

import org.hyperic.sigar.{ Sigar, SigarProxy }

import scala.collection.JavaConversions._
import scalax.file.Path
import scalax.io.JavaConverters._
import scalax.io.Resource._
import scalax.io._

trait SigarExtensionProvider {
  lazy val sigar = SigarLoader.init
}

object SigarLoader {
  val Version = "1.6.4"
  val JavaLibraryPath = "java.library.path"
  val TmpDir = "java.io.tmpdir"
  val IndexFile = "/kamon/system/native/index"
  val UsrPathField = "usr_paths"

  def init: SigarProxy = init(new File(System.getProperty(TmpDir)))

  private def init(baseTmp: File): SigarProxy = {
    val tmpDir = createTmpDir(baseTmp)
    for (lib ← loadIndex) copy(lib, tmpDir)

    attachToLibraryPath(tmpDir)

    try {
      val sigar = new Sigar
      sigar.getPid
      sigar
    } catch {
      case t: Throwable ⇒
        throw new RuntimeException("Failed to load sigar", t)
    }
  }

  private val usrPathField = {
    val usrPathField = classOf[ClassLoader].getDeclaredField(UsrPathField)
    usrPathField.setAccessible(true)
    usrPathField
  }

  private def attachToLibraryPath(dir: File): Unit = {
    val dirAbsolute = dir.getAbsolutePath
    System.setProperty(JavaLibraryPath, newLibraryPath(dirAbsolute))
    var paths = usrPathField.get(null).asInstanceOf[Array[String]]
    if (paths == null) paths = new Array[String](0)
    for (path ← paths) if (path == dirAbsolute) return
    val newPaths = util.Arrays.copyOf(paths, paths.length + 1)
    newPaths(newPaths.length - 1) = dirAbsolute
    usrPathField.set(null, newPaths)
  }

  private def newLibraryPath(dirAbsolutePath: String): String = {
    Option(System.getProperty(JavaLibraryPath)).fold(dirAbsolutePath)(oldValue ⇒ s"$dirAbsolutePath${File.pathSeparator}$oldValue")
  }

  private def copy(lib: String, tmpDir: File) {
    val dest: Path = Path(new File(tmpDir, lib))
    if (dest.exists) return
    val currentFile = Resource.fromInputStream(classOf[Loader].getResourceAsStream("" + lib))
    currentFile.acquireFor(current ⇒ dest.doCopyFrom(current.asInput))
  }

  private def createTmpDir(baseTmp: File): File = {
    val path = Path(new File(baseTmp, s"sigar-$Version"))
    path.createDirectory(failIfExists = false)
    path.jfile
  }

  private def loadIndex: util.List[String] = {
    val libs = new util.ArrayList[String]
    val input = fromInputStream(classOf[Loader].getResourceAsStream(IndexFile))
    input.lines().foreach(libs.add)
    libs
  }
}

class Loader